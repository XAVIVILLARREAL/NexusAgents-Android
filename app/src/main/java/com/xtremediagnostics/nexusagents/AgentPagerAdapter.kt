package com.xtremediagnostics.nexusagents

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class AgentPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    private val items = listOf(
        "deepseek" to { AgentFragment.newInstance(AgentConfig.AGENTS[0]) },
        "gemini" to { AgentFragment.newInstance(AgentConfig.AGENTS[1]) },
        "antigravity" to { AgentFragment.newInstance(AgentConfig.AGENTS[2]) },
        "minimax" to { AgentFragment.newInstance(AgentConfig.AGENTS[3]) },
        "codex" to { AgentFragment.newInstance(AgentConfig.AGENTS[4]) },
        "dashboard" to { DashboardFragment() }
    )

    override fun getItemCount() = items.size
    override fun createFragment(position: Int) = items[position].second()
}
