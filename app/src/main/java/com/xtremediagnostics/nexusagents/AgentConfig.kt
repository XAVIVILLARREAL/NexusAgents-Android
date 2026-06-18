package com.xtremediagnostics.nexusagents

data class AgentConfig(
    val id: String,
    val name: String,
    val url: String,
    val port: Int,
    val icon: String,
    val description: String,
    val accentColor: Long = 0xFF00E676  // Color de acento (verde por defecto)
) {
    companion object {
        val AGENTS = listOf(
            AgentConfig(
                id = "deepseek",
                name = "DeepSeek",
                url = "https://deepseek.xtremediagnostics.com",
                port = 7681,
                icon = "🧠",
                description = "DeepSeek TUI / CodeWhale — Asistente principal",
                accentColor = 0xFF00E676  // Verde Matrix
            ),
            AgentConfig(
                id = "gemini",
                name = "Gemini",
                url = "https://gemini.xtremediagnostics.com",
                port = 7682,
                icon = "✨",
                description = "Google Gemini CLI",
                accentColor = 0xFF448AFF  // Azul Google
            ),
            AgentConfig(
                id = "antigravity",
                name = "Antigravity",
                url = "https://antigravity.xtremediagnostics.com",
                port = 7683,
                icon = "🚀",
                description = "Antigravity CLI",
                accentColor = 0xFFFF6D00  // Naranja
            ),
            AgentConfig(
                id = "minimax",
                name = "Minimax",
                url = "https://minimax.xtremediagnostics.com",
                port = 7684,
                icon = "⚡",
                description = "Minimax CLI",
                accentColor = 0xFFFFD600  // Amarillo eléctrico
            ),
            AgentConfig(
                id = "codex",
                name = "Codex",
                url = "https://codex.xtremediagnostics.com",
                port = 7685,
                icon = "💻",
                description = "Codex CLI — Programación",
                accentColor = 0xFF00BCD4  // Cyan
            )
        )
    }
}
