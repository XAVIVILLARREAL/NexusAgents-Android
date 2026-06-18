package com.xtremediagnostics.nexusagents

import android.app.AlertDialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

class AgentFragment : Fragment() {

    private lateinit var agent: AgentConfig
    private lateinit var sessionManager: SessionManager
    private val webViewCache = mutableMapOf<String, WebView>()
    private val noteCache = mutableMapOf<String, String>()
    private val webViewStates = mutableMapOf<String, Bundle>()
    private var currentSessionId: String? = null
    private var currentZoom = 1.0f
    private val ZOOM_STEP = 0.05f

    private var webViewContainer: FrameLayout? = null
    private var chipGroup: ChipGroup? = null
    private var progressBar: ProgressBar? = null
    private var statusLabel: TextView? = null
    private var zoomLabel: TextView? = null
    private var zoomSlider: SeekBar? = null
    private var accentBar: View? = null

    companion object {
        fun newInstance(agent: AgentConfig) = AgentFragment().also { f ->
            f.arguments = Bundle().apply { putString("agent_id", agent.id) }
            f.agent = agent
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        agent = AgentConfig.AGENTS.find { it.id == arguments?.getString("agent_id") } ?: AgentConfig.AGENTS[0]
        sessionManager = SessionManager(agent, requireContext())
        currentZoom = ZoomPreferences.getZoom(requireContext(), agent.id)
    }

    override fun onCreateView(inflater: LayoutInflater, c: ViewGroup?, state: Bundle?): View? {
        val v = inflater.inflate(R.layout.fragment_agent, c, false)
        webViewContainer = v.findViewById(R.id.webViewContainer)
        chipGroup = v.findViewById(R.id.sessionChips)
        progressBar = v.findViewById(R.id.progressBar)
        statusLabel = v.findViewById(R.id.statusLabel)
        zoomLabel = v.findViewById(R.id.zoomLabel)
        zoomSlider = v.findViewById(R.id.zoomSlider)
        accentBar = v.findViewById(R.id.accentBar)
        accentBar?.setBackgroundColor(agent.accentColor.toInt())
        setupZoomControls(v)
        setupNotesButton(v)
        setupCopyPaste(v)
        setupSessionChips()
        switchToSession(sessionManager.sessions[0].id, false)
        return v
    }

    // =====================================================================
    // ZOOM
    // =====================================================================
    private fun setupZoomControls(root: View) {
        val slider = root.findViewById<SeekBar>(R.id.zoomSlider)!!
        slider.max = 65
        slider.progress = ((currentZoom - 0.5f) * 20).toInt().coerceIn(0, 65)
        zoomLabel?.text = "${(currentZoom * 100).toInt()}%"
        slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                if (fromUser) { currentZoom = 0.5f + p / 20f; applyZoomToWebView(); updateZoomUI() }
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) { ZoomPreferences.saveZoom(requireContext(), agent.id, currentZoom) }
        })
        root.findViewById<ImageButton>(R.id.btnZoomIn)?.setOnClickListener { adjustZoom(ZOOM_STEP) }
        root.findViewById<ImageButton>(R.id.btnZoomOut)?.setOnClickListener { adjustZoom(-ZOOM_STEP) }
        root.findViewById<ImageButton>(R.id.btnZoomReset)?.setOnClickListener { setZoom(1.0f) }
        root.findViewById<TextView>(R.id.zoomPreset100)?.setOnClickListener { setZoom(1.0f) }
        root.findViewById<TextView>(R.id.zoomPreset150)?.setOnClickListener { setZoom(1.5f) }
    }

    private fun adjustZoom(delta: Float) { setZoom((currentZoom + delta).coerceIn(0.5f, 3.75f)) }
    private fun setZoom(z: Float) {
        currentZoom = z; zoomSlider?.progress = ((z - 0.5f) * 20).toInt()
        applyZoomToWebView(); updateZoomUI()
        ZoomPreferences.saveZoom(requireContext(), agent.id, currentZoom)
    }
    private fun applyZoomToWebView() {
        // Escalar el contenedor del WebView (zoom visual real)
        webViewContainer?.apply {
            pivotX = 0f; pivotY = 0f
            scaleX = currentZoom; scaleY = currentZoom
        }
        // También aplicar textZoom para agrandar letras
        webViewCache[currentSessionId]?.settings?.textZoom = (currentZoom * 100).toInt()
    }
    private fun updateZoomUI() {
        zoomLabel?.text = "${(currentZoom * 100).toInt()}%"
        val s = sessionManager.getSession(currentSessionId ?: "")
        statusLabel?.text = "${agent.icon} ${agent.name} · ${s?.label ?: ""}"
    }

    // =====================================================================
    // NOTES & RENAME
    // =====================================================================
    private fun setupNotesButton(root: View) {
        root.findViewById<ImageButton>(R.id.btnNotes)?.setOnClickListener { showNotesDialog() }
    }

    private fun showNotesDialog() {
        val sid = currentSessionId ?: return
        val session = sessionManager.getSession(sid) ?: return
        val editText = EditText(requireContext()).apply {
            setText(noteCache[sid] ?: "")
            hint = "Objetivo / Nota para ${session.label}..."
            setTextColor(Color.parseColor("#E0F8FF")); setHintTextColor(Color.parseColor("#4A5A6E"))
            minLines = 4; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setBackgroundColor(Color.parseColor("#12141E")); setPadding(24, 24, 24, 24)
        }
        AlertDialog.Builder(requireContext())
            .setTitle("📝 ${agent.icon} ${session.label}")
            .setView(editText)
            .setPositiveButton("Guardar") { _, _ -> noteCache[sid] = editText.text.toString(); toast("Nota guardada") }
            .setNegativeButton("Cancelar", null)
            .setNeutralButton("Renombrar Sesión") { _, _ -> showRenameDialog(sid) }
            .show()
    }

    private fun showRenameDialog(sid: String) {
        val session = sessionManager.getSession(sid) ?: return
        val input = EditText(requireContext()).apply {
            setText(session.label); setTextColor(Color.parseColor("#E0F8FF"))
            setBackgroundColor(Color.parseColor("#12141E")); setPadding(24, 24, 24, 24)
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Renombrar sesión").setView(input)
            .setPositiveButton("OK") { _, _ ->
                val newLabel = input.text.toString().ifBlank { session.label }
                sessionManager.renameSession(sid, newLabel)
                refreshChips(); updateZoomUI(); toast("Renombrada a: $newLabel")
            }
            .setNegativeButton("Cancelar", null).show()
    }

    private fun toast(msg: String) = Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()

    // =====================================================================
    // COPY / PASTE
    // =====================================================================
    private fun setupCopyPaste(root: View) {
        root.findViewById<ImageButton>(R.id.btnCopy)?.setOnClickListener {
            webViewCache[currentSessionId]?.evaluateJavascript(
                "(function(){var s=window.getSelection().toString();if(!s)s=document.body.innerText.substring(0,500);return s;})()"
            ) { result ->
                val text = result?.trim('"')?.take(2000) ?: ""
                if (text.isNotBlank()) {
                    val clip = android.content.ClipData.newPlainText("terminal", text)
                    (requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                        .setPrimaryClip(clip)
                    toast("Copiado")
                }
            }
        }
        root.findViewById<ImageButton>(R.id.btnPaste)?.setOnClickListener {
            val clip = (requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0).text.toString()
                // Enviar texto al terminal vía pegado en el WebView
                webViewCache[currentSessionId]?.evaluateJavascript(
                    "var e=document.activeElement||document.body;if(e&&e.value!=null){e.value+='${text.replace("'","\\'")}';e.dispatchEvent(new Event('input',{bubbles:true}))}else{document.execCommand('insertText',false,'${text.replace("'","\\'")}')}",
                    null
                )
                toast("Pegado")
            }
        }
    }

    // =====================================================================
    // SESSION MANAGEMENT
    // =====================================================================
    private fun setupSessionChips() {
        chipGroup?.removeAllViews()
        sessionManager.sessions.forEach { addChip(it) }
        val addChip = Chip(requireContext()).apply {
            text = "+"; isCheckable = false
            setChipBackgroundColorResource(android.R.color.transparent)
            setTextColor(agent.accentColor.toInt()); setOnClickListener { addNewSession() }
        }
        chipGroup?.addView(addChip)
    }

    private fun refreshChips() {
        chipGroup?.removeAllViews()
        setupSessionChips()
        chipGroup?.let { group ->
            for (i in 0 until group.childCount) (group.getChildAt(i) as? Chip)?.isChecked = group.getChildAt(i).tag == currentSessionId
        }
    }

    private fun addChip(session: AgentSession) {
        val chip = Chip(requireContext()).apply {
            text = session.label; isCloseIconVisible = sessionManager.size > 1; tag = session.id; isCheckable = true
            setChipBackgroundColorResource(R.color.bg_elevated)
            setTextColor(ContextCompat.getColor(requireContext(), R.color.text_neon))
            chipStrokeWidth = 1f
            chipStrokeColor = ContextCompat.getColorStateList(requireContext(), R.color.chip_stroke_selector)
            setCloseIconTintResource(R.color.text_dim)
            setOnClickListener { switchToSession(session.id, true) }
            setOnCloseIconClickListener { removeSession(session.id) }
            setOnLongClickListener { showRenameDialog(session.id); true }
        }
        chipGroup?.addView(chip, chipGroup?.childCount?.minus(1) ?: 0)
    }

    private fun addNewSession() {
        val session = sessionManager.addSession()
        addChip(session); switchToSession(session.id, true)
        webViewContainer?.animate()?.scaleX(0.94f)?.scaleY(0.94f)?.setDuration(120)
            ?.withEndAction { webViewContainer?.animate()?.scaleX(1f)?.scaleY(1f)?.setDuration(200)?.setInterpolator(OvershootInterpolator(2f))?.start() }?.start()
        toast("✦ ${session.label}")
    }

    private fun removeSession(sid: String) {
        if (sessionManager.removeSession(sid)) {
            webViewCache.remove(sid)?.destroy(); webViewStates.remove(sid); noteCache.remove(sid)
            setupSessionChips()
            if (currentSessionId == sid) switchToSession(sessionManager.sessions[0].id, true)
        }
    }

    private fun switchToSession(sid: String, animate: Boolean) {
        if (currentSessionId == sid) return
        val old = webViewCache[currentSessionId]
        currentSessionId = sid
        if (animate && old != null) {
            old.animate().alpha(0f).translationX(-80f).setDuration(150)
                .withEndAction { old.visibility = View.GONE }.start()
        } else old?.visibility = View.GONE

        val wv = webViewCache.getOrPut(sid) { createWebView(sid) }
        wv.apply {
            visibility = View.VISIBLE
            if (animate) { alpha = 0f; translationX = 80f; animate().alpha(1f).translationX(0f).setDuration(200).setInterpolator(DecelerateInterpolator()).start() }
        }
        chipGroup?.let { g ->
            for (i in 0 until g.childCount) (g.getChildAt(i) as? Chip)?.isChecked = g.getChildAt(i).tag == sid
        }
        applyZoomToWebView(); updateZoomUI()
    }

    // =====================================================================
    // WEBVIEW — never-disconnect edition
    // =====================================================================

    private fun createWebView(sid: String): WebView {
        val wv = WebView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(Color.parseColor("#060608"))
            settings.apply {
                javaScriptEnabled = true; domStorageEnabled = true
                allowFileAccess = false; allowContentAccess = false
                cacheMode = WebSettings.LOAD_DEFAULT
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                useWideViewPort = false
                loadWithOverviewMode = false
                builtInZoomControls = false; displayZoomControls = false; setSupportZoom(false)
                textZoom = (currentZoom * 100).toInt()
                userAgentString = "NexusAgents/3.2 (Android) $userAgentString"
            }
            // Escala inicial del contenedor desde el primer momento
            post { applyZoomToWebView() }
            // JavaScript interface para auto-reconexión
            addJavascriptInterface(AutoReconnectBridge(sid), "NexusBridge")

            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    progressBar?.visibility = View.VISIBLE
                }
                override fun onPageFinished(view: WebView?, url: String?) {
                    progressBar?.visibility = View.GONE
                    applyZoomToWebView()
                    // Inyectar auto-reconexión
                    injectAutoReconnect(view)
                }
                override fun shouldOverrideUrlLoading(v: WebView?, r: WebResourceRequest?) = false
            }
            webChromeClient = WebChromeClient()

            // Restaurar estado guardado o cargar URL
            val savedState = webViewStates.remove(sid)
            if (savedState != null) {
                restoreState(savedState)
                // Forzar recarga para asegurar conexión fresca
                postDelayed({ reload() }, 500)
            } else {
                loadUrl(agent.url)
            }
        }
        webViewContainer?.addView(wv)
        return wv
    }

    private fun injectAutoReconnect(wv: WebView?) {
        wv?.evaluateJavascript("""
            (function(){
                if(window.__nexusReconnect) return;
                window.__nexusReconnect = true;
                var origWS = window.WebSocket;
                window.WebSocket = function(){
                    var ws = new origWS.apply(this, arguments);
                    ws.addEventListener('close', function(){
                        console.log('[Nexus] WebSocket closed — reloading...');
                        NexusBridge.onDisconnect();
                    });
                    ws.addEventListener('error', function(){
                        setTimeout(function(){ location.reload(); }, 2000);
                    });
                    return ws;
                };
                setInterval(function(){
                    if(document.body && !document.body.innerHTML.match(/cloudflared|ttyd|terminal/i)){
                        NexusBridge.onDisconnect();
                    }
                }, 15000);
            })();
        """.trimIndent(), null)
    }

    inner class AutoReconnectBridge(private val sid: String) {
        @JavascriptInterface
        fun onDisconnect() {
            wvHandler.post {
                if (currentSessionId == sid) {
                    webViewCache[sid]?.reload()
                }
            }
        }
    }
    private val wvHandler = android.os.Handler(android.os.Looper.getMainLooper())

    // =====================================================================
    // LIFECYCLE — save/restore WebView state
    // =====================================================================
    override fun onPause() {
        super.onPause()
        // Guardar WebView state al salir
        webViewCache.forEach { (sid, wv) ->
            val bundle = Bundle()
            wv.saveState(bundle)
            webViewStates[sid] = bundle
        }
    }

    override fun onResume() {
        super.onResume()
        // Verificar y re-conectar WebViews que puedan haberse desconectado
        webViewCache.forEach { (sid, wv) ->
            wv.evaluateJavascript("(function(){if(!document.body||!document.body.innerHTML.length)location.reload()})()", null)
        }
    }

    // =====================================================================
    // PUBLIC
    // =====================================================================
    fun reloadCurrentSession() = webViewCache[currentSessionId]?.reload()
    fun getSessionCount() = sessionManager.size
    fun getAgent() = agent

    override fun onDestroyView() {
        // Guardar todos los WebView states antes de destruir
        webViewCache.forEach { (sid, wv) ->
            val bundle = Bundle(); wv.saveState(bundle); webViewStates[sid] = bundle
        }
        webViewCache.values.forEach { it.stopLoading(); it.destroy() }
        webViewCache.clear()
        super.onDestroyView()
    }
}
