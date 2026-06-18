package com.xtremediagnostics.nexusagents

import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

class AgentFragment : Fragment() {

    private lateinit var agent: AgentConfig
    private lateinit var sessionManager: SessionManager

    // UI
    private var webViewContainer: FrameLayout? = null
    private var chipGroup: ChipGroup? = null
    private var progressBar: ProgressBar? = null
    private var zoomOverlay: CardView? = null
    private var statusLabel: TextView? = null
    private var accentView: View? = null

    // WebViews cache
    private val webViewCache = mutableMapOf<String, WebView>()
    private var currentSessionId: String? = null

    // Zoom
    private var zoomLevel = 1.0f
    private val ZOOM_MIN = 0.5f
    private val ZOOM_MAX = 3.0f
    private val ZOOM_STEP = 0.15f

    companion object {
        private const val ARG_AGENT_ID = "agent_id"
        fun newInstance(agent: AgentConfig): AgentFragment {
            val fragment = AgentFragment()
            fragment.arguments = Bundle().apply { putString(ARG_AGENT_ID, agent.id) }
            fragment.agent = agent
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val agentId = arguments?.getString(ARG_AGENT_ID)
        agent = AgentConfig.AGENTS.find { it.id == agentId } ?: AgentConfig.AGENTS[0]
        sessionManager = SessionManager(agent)

        // Cargar zoom guardado
        zoomLevel = ZoomPreferences.getZoom(requireContext(), agent.id)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_agent, container, false)
        webViewContainer = view.findViewById(R.id.webViewContainer)
        chipGroup = view.findViewById(R.id.sessionChips)
        progressBar = view.findViewById(R.id.progressBar)
        zoomOverlay = view.findViewById(R.id.zoomOverlay)
        statusLabel = view.findViewById(R.id.statusLabel)
        accentView = view.findViewById(R.id.accentBar)

        // Barra de acento del color del agente
        accentView?.setBackgroundColor(agent.accentColor.toInt())

        setupZoomControls(view)
        setupSessionChips()
        switchToSession(sessionManager.sessions[0].id, animate = false)

        return view
    }

    // =========================================================================
    // ZOOM — persistente por agente
    // =========================================================================

    private fun setupZoomControls(root: View) {
        root.findViewById<ImageButton>(R.id.btnZoomIn).setOnClickListener {
            zoomLevel = (zoomLevel + ZOOM_STEP).coerceAtMost(ZOOM_MAX)
            applyZoom(); showZoomHUD()
        }
        root.findViewById<ImageButton>(R.id.btnZoomOut).setOnClickListener {
            zoomLevel = (zoomLevel - ZOOM_STEP).coerceAtLeast(ZOOM_MIN)
            applyZoom(); showZoomHUD()
        }
        root.findViewById<ImageButton>(R.id.btnZoomReset).setOnClickListener {
            zoomLevel = 1.0f
            ZoomPreferences.resetZoom(requireContext(), agent.id)
            applyZoom(); showZoomHUD()
        }
        // Pinch-to-zoom nativo del WebView
    }

    private fun applyZoom() {
        webViewCache[currentSessionId]?.setInitialScale((zoomLevel * 100).toInt())
    }

    private fun showZoomHUD() {
        val pct = (zoomLevel * 100).toInt()
        val session = sessionManager.getSession(currentSessionId ?: "")
        statusLabel?.text = "${agent.icon}  ${agent.name}  ·  ${session?.label ?: ""}  ·  Zoom: $pct%"
        ZoomPreferences.saveZoom(requireContext(), agent.id, zoomLevel)

        // Mostrar overlay con animación elástica
        zoomOverlay?.apply {
            alpha = 0f
            scaleX = 0.7f
            scaleY = 0.7f
            visibility = View.VISIBLE
            animate()
                .alpha(1f)
                .scaleX(1f).scaleY(1f)
                .setDuration(250)
                .setInterpolator(OvershootInterpolator(1.5f))
                .withEndAction {
                    animate()
                        .alpha(0f)
                        .setStartDelay(1800)
                        .setDuration(400)
                        .withEndAction { visibility = View.GONE }
                        .start()
                }
                .start()
        }
    }

    // =========================================================================
    // SESIONES — chips premium con animaciones
    // =========================================================================

    private fun setupSessionChips() {
        chipGroup?.removeAllViews()
        sessionManager.sessions.forEach { addChip(it) }
        addNewSessionChip()
    }

    private fun addChip(session: AgentSession) {
        val accent = agent.accentColor.toInt()
        val chip = Chip(requireContext()).apply {
            text = session.label
            isCloseIconVisible = sessionManager.size > 1
            tag = session.id
            isCheckable = true
            setChipBackgroundColorResource(R.color.surface_light)
            setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
            chipStrokeWidth = 1f
            chipStrokeColor = ContextCompat.getColorStateList(requireContext(), R.color.chip_stroke_selector)
            setCloseIconTintResource(R.color.text_secondary)
            setOnClickListener { switchToSession(session.id, animate = true) }
            setOnCloseIconClickListener { removeSession(session.id) }
            setOnLongClickListener {
                Toast.makeText(requireContext(), session.label, Toast.LENGTH_SHORT).show()
                true
            }
        }
        chipGroup?.addView(chip, chipGroup?.childCount?.minus(1) ?: 0)
    }

    private fun addNewSessionChip() {
        val addChip = Chip(requireContext()).apply {
            text = "  +  "
            isCheckable = false
            setChipBackgroundColorResource(android.R.color.transparent)
            setTextColor(agent.accentColor.toInt())
            setOnClickListener { addNewSession() }
        }
        chipGroup?.addView(addChip)
    }

    private fun addNewSession() {
        val session = sessionManager.addSession()
        addChip(session)
        switchToSession(session.id, animate = true)
        // Animación de feedback: pulso
        webViewContainer?.animate()
            ?.scaleX(0.94f)?.scaleY(0.94f)
            ?.setDuration(120)
            ?.withEndAction {
                webViewContainer?.animate()
                    ?.scaleX(1f)?.scaleY(1f)
                    ?.setDuration(200)
                    ?.setInterpolator(OvershootInterpolator(2f))
                    ?.start()
            }?.start()
        Toast.makeText(requireContext(), "✦ ${session.label} creada", Toast.LENGTH_SHORT).show()
    }

    private fun removeSession(sessionId: String) {
        if (sessionManager.removeSession(sessionId)) {
            webViewCache.remove(sessionId)?.destroy()
            chipGroup?.removeAllViews()
            setupSessionChips()
            if (currentSessionId == sessionId) {
                switchToSession(sessionManager.sessions[0].id, animate = true)
            }
        }
    }

    private fun switchToSession(sessionId: String, animate: Boolean) {
        if (currentSessionId == sessionId) return
        val oldWV = webViewCache[currentSessionId]
        currentSessionId = sessionId

        // Fade out + slide izquierda la anterior
        if (animate && oldWV != null) {
            oldWV.animate().alpha(0f).translationX(-80f).setDuration(150)
                .withEndAction { oldWV.visibility = View.GONE }.start()
        } else {
            oldWV?.visibility = View.GONE
        }

        // Crear o mostrar WebView
        val webView = webViewCache.getOrPut(sessionId) { createWebView(sessionId) }
        webView.apply {
            visibility = View.VISIBLE
            if (animate) {
                alpha = 0f; translationX = 80f
                animate().alpha(1f).translationX(0f).setDuration(200)
                    .setInterpolator(DecelerateInterpolator()).start()
            } else {
                alpha = 1f; translationX = 0f
            }
        }

        // Chip seleccionado
        chipGroup?.let { group ->
            for (i in 0 until group.childCount) {
                (group.getChildAt(i) as? Chip)?.isChecked = (group.getChildAt(i).tag == sessionId)
            }
        }

        applyZoom()
        showZoomHUD()
    }

    // =========================================================================
    // WEBVIEW
    // =========================================================================

    private fun createWebView(sessionId: String): WebView {
        val wv = WebView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setupWebView()
            loadUrl(agent.url)
        }
        webViewContainer?.addView(wv)
        return wv
    }

    private fun WebView.setupWebView() {
        val accent = agent.accentColor.toInt()
        setBackgroundColor(Color.parseColor("#FF0A0A0A"))

        settings.apply {
            javaScriptEnabled = true; domStorageEnabled = true
            allowFileAccess = false; allowContentAccess = false
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            useWideViewPort = true; loadWithOverviewMode = true
            builtInZoomControls = true; displayZoomControls = false
            setSupportZoom(true)
            userAgentString = "NexusAgents/2.0 (Android) $userAgentString"
        }

        webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                progressBar?.visibility = View.VISIBLE
                progressBar?.progressTintList = 
                    ContextCompat.getColorStateList(requireContext(), R.color.primary)
            }
            override fun onPageFinished(view: WebView?, url: String?) {
                progressBar?.visibility = View.GONE
                applyZoom()
            }
            override fun shouldOverrideUrlLoading(v: WebView?, r: WebResourceRequest?) = false
        }
        webChromeClient = WebChromeClient()
    }

    // =========================================================================
    // PUBLIC
    // =========================================================================

    fun reloadCurrentSession() = webViewCache[currentSessionId]?.reload()
    fun getCurrentUrl() = agent.url
    fun getSessionCount() = sessionManager.size
    fun getAgent() = agent

    override fun onDestroyView() {
        webViewCache.values.forEach { it.stopLoading(); it.destroy() }
        webViewCache.clear()
        super.onDestroyView()
    }
}
