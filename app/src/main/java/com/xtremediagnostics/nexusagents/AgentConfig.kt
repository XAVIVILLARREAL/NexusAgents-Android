package com.xtremediagnostics.nexusagents

/**
 * Configuración de los agentes CLI
 */
data class AgentConfig(
    val id: String,
    val name: String,
    val url: String,
    val port: Int,
    val icon: String,      // emoji para el menú
    val description: String
) {
    companion object {
        val AGENTS = listOf(
            AgentConfig(
                id = "deepseek",
                name = "DeepSeek",
                url = "https://deepseek.xtremediagnostics.com",
                port = 7681,
                icon = "🧠",
                description = "DeepSeek TUI / CodeWhale — Asistente principal"
            ),
            AgentConfig(
                id = "gemini",
                name = "Gemini",
                url = "https://gemini.xtremediagnostics.com",
                port = 7682,
                icon = "✨",
                description = "Google Gemini CLI"
            ),
            AgentConfig(
                id = "antigravity",
                name = "Antigravity",
                url = "https://antigravity.xtremediagnostics.com",
                port = 7683,
                icon = "🚀",
                description = "Antigravity CLI"
            ),
            AgentConfig(
                id = "minimax",
                name = "Minimax",
                url = "https://minimax.xtremediagnostics.com",
                port = 7684,
                icon = "⚡",
                description = "Minimax CLI"
            ),
            AgentConfig(
                id = "codex",
                name = "Codex",
                url = "https://codex.xtremediagnostics.com",
                port = 7685,
                icon = "💻",
                description = "Codex CLI — Programación"
            )
        )
    }
}
