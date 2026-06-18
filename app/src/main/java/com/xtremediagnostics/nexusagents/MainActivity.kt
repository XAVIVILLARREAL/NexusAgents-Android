package com.xtremediagnostics.nexusagents

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
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

        pagerAdapter = AgentPagerAdapter(this)
        viewPager.adapter = pagerAdapter
        viewPager.isUserInputEnabled = true
        viewPager.offscreenPageLimit = 1

        viewPager.setPageTransformer { page, position ->
            page.apply {
                translationX = position * -width * 0.25f
                alpha = 1f - (0.25f * kotlin.math.abs(position))
            }
        }

        bottomNav.setOnItemSelectedListener { item ->
            val pos = when (item.itemId) {
                R.id.nav_deepseek -> 0; R.id.nav_gemini -> 1
                R.id.nav_antigravity -> 2; R.id.nav_minimax -> 3
                R.id.nav_codex -> 4; R.id.nav_dashboard -> 5; R.id.nav_commits -> 6
                else -> 0
            }
            viewPager.setCurrentItem(pos, true)
            true
        }

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(pos: Int) {
                if (pos < bottomNav.menu.size()) bottomNav.menu.getItem(pos).isChecked = true
                updateTitle(pos)
            }
        })

        updateTitle(0)
    }

    private fun updateTitle(pos: Int) {
        val titles = listOf("🧠 DeepSeek","✨ Gemini","🚀 Antigravity","⚡ Minimax","💻 Codex","📊 Server","📜 Commits")
        if (pos < titles.size) title = titles[pos]
    }

    override fun onCreateOptionsMenu(menu: Menu) = menuInflater.inflate(R.menu.menu_toolbar, menu).let { true }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.action_background_mode)?.isChecked = isBackgroundMode
        menu.findItem(R.id.action_pip)?.isVisible = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_pip -> { enterPipMode(); true }
        R.id.action_background_mode -> {
            isBackgroundMode = !isBackgroundMode; item.isChecked = isBackgroundMode
            if (isBackgroundMode) startBackgroundMode() else stopBackgroundMode()
            true
        }
        R.id.action_reload -> { reloadCurrentFragment(); true }
        else -> super.onOptionsItemSelected(item)
    }

    private fun enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            enterPictureInPictureMode(params)
        }
    }

    override fun onPictureInPictureModeChanged(isInPip: Boolean, config: Configuration) {
        super.onPictureInPictureModeChanged(isInPip, config)
        if (isInPip) {
            // Ocultar UI superflua en PiP
            bottomNav.visibility = android.view.View.GONE
        } else {
            bottomNav.visibility = android.view.View.VISIBLE
        }
    }

    private fun startBackgroundMode() {
        ContextCompat.startForegroundService(this, Intent(this, AgentForegroundService::class.java))
    }
    private fun stopBackgroundMode() {
        stopService(Intent(this, AgentForegroundService::class.java))
    }
    private fun reloadCurrentFragment() {
        val frag = supportFragmentManager.findFragmentByTag("f${viewPager.currentItem}")
        if (frag is AgentFragment) frag.reloadCurrentSession()
    }
    override fun onDestroy() {
        if (isBackgroundMode) stopBackgroundMode()
        super.onDestroy()
    }
}
