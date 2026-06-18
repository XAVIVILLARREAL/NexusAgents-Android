package com.xtremediagnostics.nexusagents

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class AgentPagerAdapter(
    activity: FragmentActivity,
    private val agents: List<AgentConfig>
) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = agents.size

    override fun createFragment(position: Int): Fragment {
        return AgentFragment.newInstance(agents[position])
    }

    fun getAgent(position: Int): AgentConfig = agents[position]
}
