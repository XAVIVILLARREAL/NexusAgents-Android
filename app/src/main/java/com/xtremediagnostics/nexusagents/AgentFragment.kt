package com.xtremediagnostics.nexusagents

import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import androidx.fragment.app.Fragment

class AgentFragment : Fragment() {

    private lateinit var agent: AgentConfig
    private var webView: WebView? = null
    private var progressBar: ProgressBar? = null

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
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_agent, container, false)
        webView = view.findViewById(R.id.webView)
        progressBar = view.findViewById(R.id.progressBar)
        setupWebView()
        return view
    }

    private fun setupWebView() {
        webView?.apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                allowFileAccess = false
                allowContentAccess = false
                cacheMode = WebSettings.LOAD_DEFAULT
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                
                // Optimizaciones para terminal ttyd
                useWideViewPort = true
                loadWithOverviewMode = true
                builtInZoomControls = true
                displayZoomControls = false
                setSupportZoom(true)
                
                // User-Agent personalizado
                userAgentString = "NexusAgents/1.0 (Android) $userAgentString"
            }

            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    progressBar?.visibility = View.VISIBLE
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    progressBar?.visibility = View.GONE
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    // No sobreescribir — cargar todo en el WebView
                    return false
                }
            }

            webChromeClient = WebChromeClient()

            // Cargar la URL del agente
            loadUrl(agent.url)
        }
    }

    fun reloadAgent() {
        webView?.reload()
    }

    fun getAgentUrl(): String = agent.url

    override fun onDestroyView() {
        webView?.apply {
            stopLoading()
            destroy()
        }
        webView = null
        progressBar = null
        super.onDestroyView()
    }
}
