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
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class MainActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout
    private lateinit var pagerAdapter: AgentPagerAdapter
    private var isBackgroundMode = false

    private val tabData = listOf(
        "🧠" to "DeepSeek", "🚀" to "Antigravity", "💻" to "Codex",
        "📊" to "Server", "📜" to "Commits"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        viewPager = findViewById(R.id.viewPager)
        tabLayout = findViewById(R.id.tabLayout)

        pagerAdapter = AgentPagerAdapter(this)
        viewPager.adapter = pagerAdapter
        viewPager.isUserInputEnabled = true
        viewPager.offscreenPageLimit = 1

        TabLayoutMediator(tabLayout, viewPager) { tab, pos ->
            tab.text = "${tabData[pos].first} ${tabData[pos].second}"
        }.attach()

        updateTitle(0)
    }

    private fun updateTitle(pos: Int) {
        if (pos < tabData.size) title = "${tabData[pos].first} ${tabData[pos].second}"
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
            if (isBackgroundMode) startBackgroundMode() else stopBackgroundMode(); true
        }
        R.id.action_reload -> { reloadCurrentFragment(); true }
        else -> super.onOptionsItemSelected(item)
    }

    private fun enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build())
    }

    override fun onPictureInPictureModeChanged(isInPip: Boolean, config: Configuration) {
        super.onPictureInPictureModeChanged(isInPip, config)
        tabLayout.visibility = if (isInPip) android.view.View.GONE else android.view.View.VISIBLE
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
