package com.xtremediagnostics.nexusagents

data class AgentConfig(
    val id: String, val name: String, val url: String, val port: Int,
    val icon: String, val description: String,
    val accentColor: Long = 0xFF00F0FF, val glowColor: Long = 0x4400F0FF
) {
    companion object {
        val AGENTS = listOf(
            AgentConfig("deepseek","DeepSeek","https://deepseek.xtremediagnostics.com",7681,
                "🧠","DeepSeek TUI / CodeWhale", 0xFF00F0FF, 0x4400F0FF),
            AgentConfig("antigravity","Antigravity","https://antigravity.xtremediagnostics.com",7683,
                "🚀","Antigravity CLI", 0xFFFF6600, 0x44FF6600),
            AgentConfig("codex","Codex","https://codex.xtremediagnostics.com",7685,
                "💻","Codex CLI", 0xFF00FF66, 0x4400FF66)
        )
    }
}
