package com.xtremediagnostics.nexusagents

import android.content.Intent
import android.graphics.Color
import android.net.Uri
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

class CommitsFragment : Fragment() {

    private lateinit var commitsList: LinearLayout
    private lateinit var refreshBtn: ImageButton
    private lateinit var statusText: TextView
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreateView(inflater: LayoutInflater, c: ViewGroup?, state: Bundle?): View? {
        val v = inflater.inflate(R.layout.fragment_commits, c, false)
        commitsList = v.findViewById(R.id.commitsList)
        refreshBtn = v.findViewById(R.id.btnRefresh)
        statusText = v.findViewById(R.id.statusText)
        refreshBtn.setOnClickListener { fetchCommits() }
        fetchCommits()
        return v
    }

    private fun fetchCommits() {
        statusText.text = "Cargando commits..."
        thread {
            try {
                // Forzar refresh primero
                fetchUrl("/refresh")
                Thread.sleep(1000)
                val json = fetchUrl("/commits")
                val commits = JSONArray(json)
                handler.post { showCommits(commits) }
            } catch (e: Exception) {
                handler.post { statusText.text = "Error: ${e.message}" }
            }
        }
    }

    private fun fetchUrl(path: String): String {
        val url = URL("https://mcp-server.xtremediagnostics.com/shell/exec")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("X-API-Key", "xtreme-god-v3-8f2d9a1b4c")
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true

        val cmd = """{"command":"curl -s http://localhost:7691$path 2>/dev/null || echo '[]'"}"""
        conn.outputStream.write(cmd.toByteArray())
        val resp = BufferedReader(InputStreamReader(conn.inputStream)).readText()
        val json = JSONObject(resp)
        val stdout = json.optString("stdout", "[]")
        // Extraer solo el JSON (puede tener prefijos de docker stats)
        val start = stdout.indexOf('[')
        val end = stdout.lastIndexOf(']')
        return if (start >= 0 && end > start) stdout.substring(start, end + 1) else "[]"
    }

    private fun showCommits(commits: JSONArray) {
        commitsList.removeAllViews()
        if (commits.length() == 0) {
            statusText.text = "No hay commits aún. Toca 🔄 para refrescar."
            return
        }
        statusText.text = "${commits.length()} commits recientes"

        for (i in 0 until minOf(commits.length(), 40)) {
            val c = commits.getJSONObject(i)
            addCommitCard(
                repo = c.optString("repo", "?"),
                message = c.optString("message", ""),
                author = c.optString("author", ""),
                date = c.optString("date", "").take(10),
                url = c.optString("url", "")
            )
        }
    }

    private fun addCommitCard(repo: String, message: String, author: String, date: String, url: String) {
        val card = CardView(requireContext()).apply {
            radius = 12f; cardElevation = 4f
            setCardBackgroundColor(Color.parseColor("#12141E"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 8 }
            setOnClickListener {
                if (url.isNotBlank()) startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
        }
        val inner = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL; setPadding(16, 12, 16, 12)
        }
        val repoRow = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL }
        val repoText = TextView(requireContext()).apply {
            text = repo; setTextColor(Color.parseColor("#00F0FF")); textSize = 11f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        val dateText = TextView(requireContext()).apply {
            text = "  $date"; setTextColor(Color.parseColor("#4A5A6E")); textSize = 10f
        }
        repoRow.addView(repoText); repoRow.addView(dateText)
        val msgText = TextView(requireContext()).apply {
            text = message.take(100); setTextColor(Color.parseColor("#E0F8FF")); textSize = 13f
            maxLines = 2
        }
        val authorText = TextView(requireContext()).apply {
            text = author; setTextColor(Color.parseColor("#7A8A9E")); textSize = 10f
        }
        inner.addView(repoRow); inner.addView(msgText); inner.addView(authorText)
        card.addView(inner)
        commitsList.addView(card)
    }
}
