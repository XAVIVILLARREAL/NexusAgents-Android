package com.xtremediagnostics.nexusagents

import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
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
    private var zoomOverlay: LinearLayout? = null
    private var currentSessionLabel: TextView? = null

    // WebViews cache: sessionId -> WebView
    private val webViewCache = mutableMapOf<String, WebView>()
    private var currentSessionId: String? = null

    private var zoomLevel = 1.0f
    private val ZOOM_MIN = 0.5f
    private val ZOOM_MAX = 3.0f
    private val ZOOM_STEP = 0.2f

    companion object {
        private const val ARG_AGENT_ID = "agent_id"

        fun newInstance(agent: AgentConfig): AgentFragment {
            val fragment = AgentFragment()
            fragment.arguments = Bundle().apply {
                putString(ARG_AGENT_ID, agent.id)
            }
            fragment.agent = agent
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val agentId = arguments?.getString(ARG_AGENT_ID)
        agent = AgentConfig.AGENTS.find { it.id == agentId } ?: AgentConfig.AGENTS[0]
        sessionManager = SessionManager(agent)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_agent, container, false)

        chipGroup = view.findViewById(R.id.sessionChips)
        webViewContainer = view.findViewById(R.id.webViewContainer)
        progressBar = view.findViewById(R.id.progressBar)
        zoomOverlay = view.findViewById(R.id.zoomOverlay)
        currentSessionLabel = view.findViewById(R.id.currentSessionLabel)

        setupZoomControls(view)
        setupSessionChips()
        switchToSession(sessionManager.sessions[0].id, animate = false)

        return view
    }

    // =========================================================================
    // ZOOM
    // =========================================================================

    private fun setupZoomControls(root: View) {
        val btnZoomIn = root.findViewById<ImageButton>(R.id.btnZoomIn)
        val btnZoomOut = root.findViewById<ImageButton>(R.id.btnZoomOut)
        val btnZoomReset = root.findViewById<ImageButton>(R.id.btnZoomReset)

        btnZoomIn.setOnClickListener { zoomIn() }
        btnZoomOut.setOnClickListener { zoomOut() }
        btnZoomReset.setOnClickListener { zoomReset() }

        // Pinch-to-zoom se habilita en setupWebView()
    }

    private fun zoomIn() {
        zoomLevel = (zoomLevel + ZOOM_STEP).coerceAtMost(ZOOM_MAX)
        applyZoom()
        showZoomLevel()
    }

    private fun zoomOut() {
        zoomLevel = (zoomLevel - ZOOM_STEP).coerceAtLeast(ZOOM_MIN)
        applyZoom()
        showZoomLevel()
    }

    private fun zoomReset() {
        zoomLevel = 1.0f
        applyZoom()
        showZoomLevel()
    }

    private fun applyZoom() {
        webViewCache[currentSessionId]?.apply {
            setInitialScale((zoomLevel * 100).toInt())
        }
    }

    private fun showZoomLevel() {
        val pct = (zoomLevel * 100).toInt()
        currentSessionLabel?.text = "${agent.icon} ${agent.name} · ${sessionManager.getSession(currentSessionId ?: "")?.label} · Zoom: ${pct}%"

        // Mostrar overlay brevemente
        zoomOverlay?.apply {
            alpha = 1f
            visibility = View.VISIBLE
            animate()
                .alpha(0f)
                .setStartDelay(2000)
                .setDuration(500)
                .withEndAction { visibility = View.GONE }
                .start()
        }
    }

    // =========================================================================
    // SESSION MANAGEMENT
    // =========================================================================

    private fun setupSessionChips() {
        chipGroup?.removeAllViews()
        sessionManager.sessions.forEach { session ->
            addChip(session)
        }
        // Chip "+" para nueva sesión
        val addChip = Chip(requireContext()).apply {
            text = "+"
            isCheckable = false
            setChipBackgroundColorResource(android.R.color.transparent)
            setTextColor(ContextCompat.getColor(requireContext(), R.color.primary))
            setOnClickListener { addNewSession() }
        }
        chipGroup?.addView(addChip)
    }

    private fun addChip(session: AgentSession) {
        val chip = Chip(requireContext()).apply {
            text = session.label
            isCloseIconVisible = sessionManager.size > 1
            tag = session.id
            setChipBackgroundColorResource(R.color.surface_dark)
            setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
            setCloseIconTintResource(R.color.text_secondary)

            setOnClickListener {
                switchToSession(session.id, animate = true)
            }

            setOnCloseIconClickListener {
                removeSession(session.id)
            }
        }
        chipGroup?.addView(chip, chipGroup?.childCount?.minus(1) ?: 0)
    }

    private fun addNewSession() {
        val session = sessionManager.addSession()
        addChip(session)
        // Mostrar chip "+" al final
        switchToSession(session.id, animate = true)
        // Animación de feedback
        webViewContainer?.animate()
            ?.scaleX(0.95f)?.scaleY(0.95f)
            ?.setDuration(100)
            ?.withEndAction {
                webViewContainer?.animate()
                    ?.scaleX(1f)?.scaleY(1f)
                    ?.setDuration(150)
                    ?.start()
            }
            ?.start()

        Toast.makeText(requireContext(), "Nueva sesión: ${session.label}", Toast.LENGTH_SHORT).show()
    }

    private fun removeSession(sessionId: String) {
        if (sessionManager.removeSession(sessionId)) {
            // Limpiar WebView
            webViewCache.remove(sessionId)?.destroy()
            // Reconstruir chips
            chipGroup?.removeAllViews()
            setupSessionChips()

            if (currentSessionId == sessionId) {
                switchToSession(sessionManager.sessions[0].id, animate = true)
            }

            // Animación
            webViewContainer?.animate()
                ?.alpha(0.5f)
                ?.setDuration(120)
                ?.withEndAction {
                    webViewContainer?.animate()?.alpha(1f)?.setDuration(120)?.start()
                }
                ?.start()
        }
    }

    private fun switchToSession(sessionId: String, animate: Boolean) {
        if (currentSessionId == sessionId) return

        val oldWebView = webViewCache[currentSessionId]
        currentSessionId = sessionId

        // Ocultar WebView anterior con animación
        if (animate && oldWebView != null) {
            oldWebView.animate()
                .alpha(0f)
                .translationX(-100f)
                .setDuration(180)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction { oldWebView.visibility = View.GONE }
                .start()
        } else {
            oldWebView?.visibility = View.GONE
        }

        // Mostrar o crear nuevo WebView
        val webView = webViewCache.getOrPut(sessionId) {
            createWebView(sessionId)
        }

        webView.apply {
            visibility = View.VISIBLE
            alpha = if (animate) 0f else 1f
            translationX = if (animate) 100f else 0f

            if (animate) {
                animate()
                    .alpha(1f)
                    .translationX(0f)
                    .setDuration(200)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }
        }

        // Actualizar chip seleccionado
        chipGroup?.let { group ->
            for (i in 0 until group.childCount) {
                val child = group.getChildAt(i)
                if (child is Chip && child.tag == sessionId) {
                    child.isChecked = true
                } else if (child is Chip) {
                    child.isChecked = false
                }
            }
        }

        // Aplicar zoom actual
        applyZoom()
        showZoomLevel()
    }

    private fun createWebView(sessionId: String): WebView {
        val webView = WebView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setupWebViewSettings()
            loadUrl(agent.url)
        }
        webViewContainer?.addView(webView)
        return webView
    }

    // =========================================================================
    // WEBVIEW SETUP
    // =========================================================================

    private fun setupWebViewSettings() {
        // Shared settings applied to each new WebView
    }

    private fun WebView.setupWebViewSettings() {
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

            // Zoom settings
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = true
            displayZoomControls = false  // Ocultar botones nativos, usamos overlay
            setSupportZoom(true)
            defaultZoom = WebSettings.ZoomDensity.MEDIUM

            // Terminal optimizations
            userAgentString = "NexusAgents/1.1 (Android) $userAgentString"
        }

        webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                progressBar?.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                progressBar?.visibility = View.GONE
                // Aplicar zoom al cargar
                applyZoom()
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean = false
        }

        webChromeClient = WebChromeClient()
    }

    // =========================================================================
    // PUBLIC API
    // =========================================================================

    fun reloadCurrentSession() {
        webViewCache[currentSessionId]?.reload()
    }

    fun getCurrentUrl(): String = agent.url

    fun getSessionCount(): Int = sessionManager.size

    override fun onDestroyView() {
        webViewCache.values.forEach {
            it.stopLoading()
            it.destroy()
        }
        webViewCache.clear()
        webViewContainer = null
        chipGroup = null
        progressBar = null
        zoomOverlay = null
        currentSessionLabel = null
        super.onDestroyView()
    }
}
