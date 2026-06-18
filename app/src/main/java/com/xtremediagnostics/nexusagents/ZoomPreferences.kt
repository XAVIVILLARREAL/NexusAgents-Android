package com.xtremediagnostics.nexusagents

import android.content.Context

/**
 * Persistencia de preferencias de zoom por agente.
 * Cada agente guarda su nivel de zoom independientemente.
 */
object ZoomPreferences {
    private const val PREFS_NAME = "nexus_agents_prefs"
    private const val KEY_ZOOM_PREFIX = "zoom_"
    private const val KEY_BG_MODE = "background_mode"
    private const val DEFAULT_ZOOM = 1.0f

    fun getZoom(context: Context, agentId: String): Float {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getFloat(KEY_ZOOM_PREFIX + agentId, DEFAULT_ZOOM)
    }

    fun saveZoom(context: Context, agentId: String, zoom: Float) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putFloat(KEY_ZOOM_PREFIX + agentId, zoom).apply()
    }

    fun resetZoom(context: Context, agentId: String) {
        saveZoom(context, agentId, DEFAULT_ZOOM)
    }

    fun isBackgroundModeEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_BG_MODE, false)
    }

    fun setBackgroundMode(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_BG_MODE, enabled).apply()
    }
}
