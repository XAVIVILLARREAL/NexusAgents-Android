package com.xtremediagnostics.nexusagents

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var pagerAdapter: AgentPagerAdapter
    private var isBackgroundMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        viewPager = findViewById(R.id.viewPager)
        bottomNav = findViewById(R.id.bottomNav)

        // Configurar ViewPager2
        pagerAdapter = AgentPagerAdapter(this, AgentConfig.AGENTS)
        viewPager.adapter = pagerAdapter
        viewPager.isUserInputEnabled = true

        // Sincronizar BottomNavigation con ViewPager
        bottomNav.setOnItemSelectedListener { item ->
            val position = when (item.itemId) {
                R.id.nav_deepseek -> 0
                R.id.nav_gemini -> 1
                R.id.nav_antigravity -> 2
                R.id.nav_minimax -> 3
                R.id.nav_codex -> 4
                else -> 0
            }
            viewPager.setCurrentItem(position, true)
            true
        }

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                bottomNav.menu.getItem(position).isChecked = true
                updateTitle(position)
            }
        })

        // Título inicial
        updateTitle(0)
    }

    private fun updateTitle(position: Int) {
        val agent = AgentConfig.AGENTS[position]
        title = "${agent.icon} ${agent.name}"
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_toolbar, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val bgItem = menu.findItem(R.id.action_background_mode)
        bgItem?.isChecked = isBackgroundMode
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_background_mode -> {
                isBackgroundMode = !isBackgroundMode
                item.isChecked = isBackgroundMode
                if (isBackgroundMode) {
                    startBackgroundMode()
                } else {
                    stopBackgroundMode()
                }
                true
            }
            R.id.action_reload -> {
                reloadCurrentAgent()
                true
            }
            R.id.action_about -> {
                // Placeholder para about dialog
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun startBackgroundMode() {
        val serviceIntent = Intent(this, AgentForegroundService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
    }

    private fun stopBackgroundMode() {
        val serviceIntent = Intent(this, AgentForegroundService::class.java)
        stopService(serviceIntent)
    }

    private fun reloadCurrentAgent() {
        val fragment = supportFragmentManager
            .findFragmentByTag("f${viewPager.currentItem}")
        if (fragment is AgentFragment) {
            fragment.reloadAgent()
        }
    }

    override fun onDestroy() {
        if (isBackgroundMode) {
            stopBackgroundMode()
        }
        super.onDestroy()
    }
}
