package com.xtremediagnostics.nexusagents

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class AgentPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    private val items = listOf(
        "deepseek" to { AgentFragment.newInstance(AgentConfig.AGENTS[0]) },
        "antigravity" to { AgentFragment.newInstance(AgentConfig.AGENTS[1]) },
        "codex" to { AgentFragment.newInstance(AgentConfig.AGENTS[2]) },
        "dashboard" to { DashboardFragment() },
        "commits" to { CommitsFragment() }
    )

    override fun getItemCount() = items.size
    override fun createFragment(position: Int) = items[position].second()
}
