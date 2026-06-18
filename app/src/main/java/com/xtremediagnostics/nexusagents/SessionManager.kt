package com.xtremediagnostics.nexusagents

import java.util.UUID

/**
 * Representa una sesión individual dentro de un agente.
 * Cada sesión puede conectarse al mismo agente y verse de forma independiente.
 */
data class AgentSession(
    val id: String = UUID.randomUUID().toString().take(8),
    val agentId: String,
    var label: String = "Sesión 1",
    var url: String = ""
) {
    companion object {
        fun createDefault(agent: AgentConfig, index: Int): AgentSession {
            return AgentSession(
                agentId = agent.id,
                label = "Sesión ${index + 1}",
                url = agent.url
            )
        }
    }
}

/**
 * Gestiona las sesiones activas de un agente.
 */
class SessionManager(private val agent: AgentConfig) {
    private val _sessions = mutableListOf<AgentSession>()
    val sessions: List<AgentSession> get() = _sessions

    init {
        // Crear sesión inicial
        _sessions.add(AgentSession.createDefault(agent, 0))
    }

    fun addSession(): AgentSession {
        val session = AgentSession.createDefault(agent, _sessions.size)
        _sessions.add(session)
        return session
    }

    fun removeSession(sessionId: String): Boolean {
        if (_sessions.size <= 1) return false // No eliminar la última
        _sessions.removeAll { it.id == sessionId }
        // Renombrar secuencialmente
        _sessions.forEachIndexed { index, s ->
            _sessions[index] = s.copy(label = "Sesión ${index + 1}")
        }
        return true
    }

    fun getSession(sessionId: String): AgentSession? {
        return _sessions.find { it.id == sessionId }
    }

    fun getSessionIndex(sessionId: String): Int {
        return _sessions.indexOfFirst { it.id == sessionId }.coerceAtLeast(0)
    }

    val size: Int get() = _sessions.size
}
