package com.xtremediagnostics.nexusagents

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class AgentSession(
    val id: String = UUID.randomUUID().toString().take(8),
    val agentId: String,
    var label: String = "Sesión 1",
    var url: String = ""
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("agentId", agentId); put("label", label); put("url", url)
    }

    companion object {
        fun fromJson(o: JSONObject) = AgentSession(
            id = o.optString("id"),
            agentId = o.optString("agentId"),
            label = o.optString("label", "Sesión"),
            url = o.optString("url")
        )
        fun createDefault(agent: AgentConfig, index: Int) = AgentSession(
            agentId = agent.id, label = "Sesión ${index + 1}", url = agent.url
        )
    }
}

class SessionManager(private val agent: AgentConfig, private val context: Context) {
    private val _sessions = mutableListOf<AgentSession>()
    val sessions: List<AgentSession> get() = _sessions

    init {
        load()
        if (_sessions.isEmpty()) {
            _sessions.add(AgentSession.createDefault(agent, 0))
            save()
        }
    }

    private fun prefs(): SharedPreferences =
        context.getSharedPreferences("nexus_sessions_${agent.id}", Context.MODE_PRIVATE)

    private fun load() {
        val json = prefs().getString("sessions", null) ?: return
        try {
            val arr = JSONArray(json)
            _sessions.clear()
            for (i in 0 until arr.length()) _sessions.add(AgentSession.fromJson(arr.getJSONObject(i)))
        } catch (_: Exception) {}
    }

    fun save() {
        val arr = JSONArray().apply { _sessions.forEach { put(it.toJson()) } }
        prefs().edit().putString("sessions", arr.toString()).apply()
    }

    fun addSession(): AgentSession {
        val session = AgentSession.createDefault(agent, _sessions.size)
        _sessions.add(session)
        save()
        return session
    }

    fun removeSession(sessionId: String): Boolean {
        if (_sessions.size <= 1) return false
        _sessions.removeAll { it.id == sessionId }
        _sessions.forEachIndexed { idx, s -> if (s.label.startsWith("Sesión")) _sessions[idx] = s.copy(label = "Sesión ${idx + 1}") }
        save()
        return true
    }

    fun renameSession(sessionId: String, newLabel: String) {
        val idx = _sessions.indexOfFirst { it.id == sessionId }
        if (idx >= 0) { _sessions[idx] = _sessions[idx].copy(label = newLabel); save() }
    }

    fun getSession(sessionId: String) = _sessions.find { it.id == sessionId }
    val size get() = _sessions.size
}
