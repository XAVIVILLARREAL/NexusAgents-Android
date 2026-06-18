package com.xtremediagnostics.nexusagents

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class DashboardFragment : Fragment() {

    private lateinit var statusCards: LinearLayout
    private lateinit var ramBar: ProgressBar
    private lateinit var ramText: TextView
    private lateinit var uptimeText: TextView
    private lateinit var refreshBtn: ImageButton
    private val handler = Handler(Looper.getMainLooper())
    private var autoRefresh = true
    private val refreshRunnable = object : Runnable {
        override fun run() {
            if (autoRefresh && isAdded) { fetchStats(); handler.postDelayed(this, 8000) }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, c: ViewGroup?, state: Bundle?): View? {
        val v = inflater.inflate(R.layout.fragment_dashboard, c, false)
        statusCards = v.findViewById(R.id.statusCards)
        ramBar = v.findViewById(R.id.ramBar)
        ramText = v.findViewById(R.id.ramText)
        uptimeText = v.findViewById(R.id.uptimeText)
        refreshBtn = v.findViewById(R.id.btnRefresh)
        refreshBtn.setOnClickListener { fetchStats() }
        fetchStats()
        handler.postDelayed(refreshRunnable, 8000)
        return v
    }

    private fun fetchStats() {
        thread {
            try {
                val json = fetchFromBridge()
                handler.post { updateUI(json) }
            } catch (e: Exception) {
                handler.post { Toast.makeText(context, "Sin conexión al servidor", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    private fun fetchFromBridge(): JSONObject {
        val url = URL("https://mcp-server.xtremediagnostics.com/shell/exec")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("X-API-Key", "xtreme-god-v3-8f2d9a1b4c")
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true

        val cmd = """{"command":"docker stats --no-stream --format '{\"name\":\"{{.Name}}\",\"cpu\":\"{{.CPUPerc}}\",\"mem\":\"{{.MemUsage}}\"}' nexus-deepseek nexus-antigravity nexus-codex cloudflared-deepseek 2>/dev/null; echo '---RAM---'; free -m | grep Mem; echo '---UP---'; cat /proc/uptime"}"""
        conn.outputStream.write(cmd.toByteArray())

        val resp = BufferedReader(InputStreamReader(conn.inputStream)).readText()
        return JSONObject(resp)
    }

    private fun updateUI(json: JSONObject) {
        try {
            val stdout = json.optString("stdout", "")
            val parts = stdout.split("---RAM---")
            val containerPart = parts.getOrNull(0) ?: ""
            val rest = parts.getOrNull(1)?.split("---UP---") ?: listOf("","")
            val ramPart = rest.getOrNull(0) ?: ""
            val upPart = rest.getOrNull(1)?.trim() ?: ""

            // Parse containers
            val containers = mutableListOf<JSONObject>()
            for (line in containerPart.split("\n")) {
                val trimmed = line.trim()
                if (trimmed.startsWith("{")) {
                    try { containers.add(JSONObject(trimmed)) } catch (_: Exception) {}
                }
            }

            // RAM
            val ramParts = ramPart.split("\\s+".toRegex()).filter { it.isNotBlank() }
            val ramTotal = ramParts.getOrNull(1)?.toIntOrNull() ?: 0
            val ramUsed = ramParts.getOrNull(2)?.toIntOrNull() ?: 0
            ramBar.max = ramTotal
            ramBar.progress = ramUsed
            ramText.text = "RAM: ${ramUsed} / ${ramTotal} MB  (${if (ramTotal>0) ramUsed*100/ramTotal else 0}%)"
            ramBar.progressTintList = android.content.res.ColorStateList.valueOf(
                when { ramUsed > ramTotal * 0.85 -> Color.parseColor("#FFFF00E5")
                       ramUsed > ramTotal * 0.7  -> Color.parseColor("#FFFF6600")
                       else -> Color.parseColor("#FF00F0FF") })

            // Uptime
            val upSec = upPart.split(" ").getOrNull(0)?.toFloatOrNull()?.toInt() ?: 0
            val hours = upSec / 3600; val mins = (upSec % 3600) / 60
            uptimeText.text = "Uptime: ${hours}h ${mins}m"

            // Container cards
            statusCards.removeAllViews()
            val colors = mapOf(
                "nexus-deepseek" to "#00F0FF", "nexus-antigravity" to "#FF6600",
                "nexus-codex" to "#00FF66", "cloudflared-deepseek" to "#AA00FF"
            )
            for (c in containers) {
                val name = c.optString("name", "?").replace("nexus-", "")
                val cpu = c.optString("cpu", "0%").trim('%').toFloatOrNull() ?: 0f
                val mem = c.optString("mem", "0MiB")
                val color = colors[c.optString("name")] ?: "#888888"
                addStatusCard(name, "CPU: ${cpu.toInt()}%", "RAM: $mem", Color.parseColor(color))
            }

        } catch (_: Exception) {}
    }

    private fun addStatusCard(title: String, cpu: String, mem: String, color: Int) {
        val card = CardView(requireContext()).apply {
            radius = 16f; cardElevation = 8f
            setCardBackgroundColor(Color.parseColor("#14141C"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 12 }
        }
        val inner = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(20, 16, 20, 16)
        }
        val dot = View(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(12, 12).apply { marginEnd = 16; gravity = android.view.Gravity.CENTER_VERTICAL }
            setBackgroundColor(color)
        }
        val texts = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        val titleTv = TextView(requireContext()).apply {
            text = title; setTextColor(Color.parseColor("#E0F8FF")); textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        val detailTv = TextView(requireContext()).apply {
            text = "$cpu  ·  $mem"; setTextColor(Color.parseColor("#7A8A9E")); textSize = 12f
        }
        texts.addView(titleTv); texts.addView(detailTv)
        inner.addView(dot); inner.addView(texts)
        card.addView(inner)
        statusCards.addView(card)
    }

    override fun onDestroyView() {
        autoRefresh = false
        handler.removeCallbacks(refreshRunnable)
        super.onDestroyView()
    }
}
