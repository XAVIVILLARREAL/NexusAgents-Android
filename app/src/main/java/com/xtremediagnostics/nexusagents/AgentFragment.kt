package com.xtremediagnostics.nexusagents

import android.view.animation.OvershootInterpolator
import android.app.AlertDialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
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
    private var currentSessionId: String? = null
    private var currentZoom = 1.0f
    private val ZOOM_STEP = 0.05f

    // UI
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
        val id = arguments?.getString("agent_id")
        agent = AgentConfig.AGENTS.find { it.id == id } ?: AgentConfig.AGENTS[0]
        sessionManager = SessionManager(agent)
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
        setupSessionChips()
        switchToSession(sessionManager.sessions[0].id, false)
        return v
    }

    // =====================================================================
    // ZOOM SYSTEM — Slider + botones + presets + WebView JS injection
    // =====================================================================

    private fun setupZoomControls(root: View) {
        val slider = root.findViewById<SeekBar>(R.id.zoomSlider)!!
        // Map slider 0–65 → zoom 0.5x – 3.75x (20 = 1.5x default)
        slider.max = 65
        slider.progress = ((currentZoom - 0.5f) * 20).toInt().coerceIn(0, 65)
        zoomLabel?.text = "${(currentZoom * 100).toInt()}%"

        slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                if (fromUser) {
                    currentZoom = 0.5f + p / 20f
                    applyZoomToWebView()
                    updateZoomUI()
                }
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {
                ZoomPreferences.saveZoom(requireContext(), agent.id, currentZoom)
            }
        })

        root.findViewById<ImageButton>(R.id.btnZoomIn)?.setOnClickListener {
            currentZoom = (currentZoom + ZOOM_STEP).coerceAtMost(3.75f)
            slider.progress = ((currentZoom - 0.5f) * 20).toInt()
            applyZoomToWebView(); updateZoomUI()
            ZoomPreferences.saveZoom(requireContext(), agent.id, currentZoom)
        }
        root.findViewById<ImageButton>(R.id.btnZoomOut)?.setOnClickListener {
            currentZoom = (currentZoom - ZOOM_STEP).coerceAtLeast(0.5f)
            slider.progress = ((currentZoom - 0.5f) * 20).toInt()
            applyZoomToWebView(); updateZoomUI()
            ZoomPreferences.saveZoom(requireContext(), agent.id, currentZoom)
        }
        root.findViewById<ImageButton>(R.id.btnZoomReset)?.setOnClickListener {
            currentZoom = 1.0f
            slider.progress = ((currentZoom - 0.5f) * 20).toInt()
            applyZoomToWebView(); updateZoomUI()
            ZoomPreferences.saveZoom(requireContext(), agent.id, currentZoom)
        }
        // Presets
        root.findViewById<TextView>(R.id.zoomPreset100)?.setOnClickListener {
            currentZoom = 1.0f; slider.progress = 10
            applyZoomToWebView(); updateZoomUI()
            ZoomPreferences.saveZoom(requireContext(), agent.id, currentZoom)
        }
        root.findViewById<TextView>(R.id.zoomPreset150)?.setOnClickListener {
            currentZoom = 1.5f; slider.progress = 20
            applyZoomToWebView(); updateZoomUI()
            ZoomPreferences.saveZoom(requireContext(), agent.id, currentZoom)
        }
    }

    private fun applyZoomToWebView() {
        webViewCache[currentSessionId]?.let { wv ->
            // Inyectar zoom vía JS para escalar el CONTENIDO del navegador
            val js = "document.body.style.zoom='${currentZoom}'"
            wv.evaluateJavascript(js, null)
        }
    }

    private fun updateZoomUI() {
        val pct = (currentZoom * 100).toInt()
        zoomLabel?.text = "${pct}%"
        val s = sessionManager.getSession(currentSessionId ?: "")
        statusLabel?.text = "${agent.icon} ${agent.name} · ${s?.label ?: ""}"
    }

    // =====================================================================
    // QUICK NOTES — Programming flow tool
    // =====================================================================

    private fun setupNotesButton(root: View) {
        root.findViewById<ImageButton>(R.id.btnNotes)?.setOnClickListener {
            showNotesDialog()
        }
    }

    private fun showNotesDialog() {
        val sid = currentSessionId ?: return
        val session = sessionManager.getSession(sid) ?: return
        val existingNote = noteCache[sid] ?: ""

        val editText = EditText(requireContext()).apply {
            setText(existingNote)
            hint = "Objetivo / Nota para ${session.label}..."
            setTextColor(Color.parseColor("#E0F8FF"))
            setHintTextColor(Color.parseColor("#4A5A6E"))
            minLines = 4
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setBackgroundColor(Color.parseColor("#12141E"))
            setPadding(24, 24, 24, 24)
        }

        AlertDialog.Builder(requireContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("📝 ${agent.icon} ${session.label}")
            .setView(editText)
            .setPositiveButton("Guardar") { _, _ ->
                noteCache[sid] = editText.text.toString()
                Toast.makeText(requireContext(), "Nota guardada", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancelar", null)
            .setNeutralButton("Renombrar Sesión") { _, _ -> showRenameDialog(sid) }
            .show()
    }

    private fun showRenameDialog(sessionId: String) {
        val session = sessionManager.getSession(sessionId) ?: return
        val input = EditText(requireContext()).apply {
            setText(session.label)
            setTextColor(Color.parseColor("#E0F8FF"))
            setBackgroundColor(Color.parseColor("#12141E"))
            setPadding(24, 24, 24, 24)
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Renombrar sesión")
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                val newLabel = input.text.toString().ifBlank { session.label }
                val idx = sessionManager.sessions.indexOfFirst { it.id == sessionId }
                if (idx >= 0) {
                    sessionManager.sessions.toMutableList()[idx] = session.copy(label = newLabel)
                }
                refreshChips()
                updateZoomUI()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // =====================================================================
    // SESSION MANAGEMENT
    // =====================================================================

    private fun setupSessionChips() {
        chipGroup?.removeAllViews()
        sessionManager.sessions.forEach { addChip(it) }
        val addChip = Chip(requireContext()).apply {
            text = "+"
            isCheckable = false
            setChipBackgroundColorResource(android.R.color.transparent)
            setTextColor(agent.accentColor.toInt())
            setOnClickListener { addNewSession() }
        }
        chipGroup?.addView(addChip)
    }

    private fun refreshChips() {
        chipGroup?.removeAllViews()
        setupSessionChips()
        // Restore checked state
        chipGroup?.let { group ->
            for (i in 0 until group.childCount) {
                val child = group.getChildAt(i)
                if (child is Chip && child.tag == currentSessionId) child.isChecked = true
            }
        }
    }

    private fun addChip(session: AgentSession) {
        val chip = Chip(requireContext()).apply {
            text = session.label
            isCloseIconVisible = sessionManager.size > 1
            tag = session.id
            isCheckable = true
            setChipBackgroundColorResource(R.color.bg_elevated)
            setTextColor(ContextCompat.getColor(requireContext(), R.color.text_neon))
            chipStrokeWidth = 1f
            chipStrokeColor = ContextCompat.getColorStateList(requireContext(), R.color.chip_stroke_selector)
            setCloseIconTintResource(R.color.text_dim)
            setOnClickListener { switchToSession(session.id, true) }
            setOnCloseIconClickListener { removeSession(session.id) }
            setOnLongClickListener {
                showRenameDialog(session.id)
                true
            }
        }
        chipGroup?.addView(chip, chipGroup?.childCount?.minus(1) ?: 0)
    }

    private fun addNewSession() {
        val session = sessionManager.addSession()
        addChip(session)
        switchToSession(session.id, true)
        webViewContainer?.animate()?.scaleX(0.94f)?.scaleY(0.94f)?.setDuration(120)
            ?.withEndAction {
                webViewContainer?.animate()?.scaleX(1f)?.scaleY(1f)
                    ?.setDuration(200)?.setInterpolator(OvershootInterpolator(2f))?.start()
            }?.start()
        Toast.makeText(requireContext(), "✦ ${session.label}", Toast.LENGTH_SHORT).show()
    }

    private fun removeSession(sid: String) {
        if (sessionManager.removeSession(sid)) {
            webViewCache.remove(sid)?.destroy()
            noteCache.remove(sid)
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
            if (animate) { alpha = 0f; translationX = 80f
                animate().alpha(1f).translationX(0f).setDuration(200)
                    .setInterpolator(DecelerateInterpolator()).start()
            }
        }

        chipGroup?.let { g ->
            for (i in 0 until g.childCount) (g.getChildAt(i) as? Chip)?.isChecked = g.getChildAt(i).tag == sid
        }
        applyZoomToWebView()
        updateZoomUI()
    }

    // =====================================================================
    // WEBVIEW
    // =====================================================================

    private fun createWebView(sid: String): WebView {
        val wv = WebView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(Color.parseColor("#060608"))
            settings.apply {
                javaScriptEnabled = true; domStorageEnabled = true
                allowFileAccess = false; allowContentAccess = false
                cacheMode = WebSettings.LOAD_DEFAULT
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                useWideViewPort = true; loadWithOverviewMode = true
                builtInZoomControls = false; displayZoomControls = false
                setSupportZoom(false)
                textZoom = 100
                userAgentString = "NexusAgents/3.0 (Android) $userAgentString"
            }
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    progressBar?.visibility = View.VISIBLE
                }
                override fun onPageFinished(view: WebView?, url: String?) {
                    progressBar?.visibility = View.GONE
                    applyZoomToWebView()
                }
                override fun shouldOverrideUrlLoading(v: WebView?, r: WebResourceRequest?) = false
            }
            webChromeClient = WebChromeClient()
            loadUrl(agent.url)
        }
        webViewContainer?.addView(wv)
        return wv
    }

    // =====================================================================
    // PUBLIC
    // =====================================================================

    fun reloadCurrentSession() = webViewCache[currentSessionId]?.reload()
    fun getSessionCount() = sessionManager.size
    fun getAgent() = agent

    override fun onDestroyView() {
        webViewCache.values.forEach { it.stopLoading(); it.destroy() }
        webViewCache.clear()
        super.onDestroyView()
    }
}
