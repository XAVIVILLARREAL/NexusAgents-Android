package com.xtremediagnostics.nexusagents

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class NexusAgentsApp : Application() {

    companion object {
        const val CHANNEL_ID = "nexus_agents_channel"
        const val NOTIFICATION_ID = 1001
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Nexus Agents",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notificación persistente de agentes activos"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
