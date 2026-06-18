package com.xtremediagnostics.nexusagents

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat

/**
 * Foreground Service que mantiene viva la conexión a los agentes
 * aunque el celular esté bloqueado. El trabajo real ocurre en el servidor;
 * este servicio solo evita que Android mate el proceso de la app.
 */
class AgentForegroundService : Service() {

    private lateinit var wakeLock: PowerManager.WakeLock

    override fun onCreate() {
        super.onCreate()
        // WakeLock parcial para mantener WiFi activo
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "NexusAgents::AgentKeepAlive"
        )
        wakeLock.acquire(30 * 60 * 1000L) // 30 minutos máximo
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, NexusAgentsApp.CHANNEL_ID)
            .setContentTitle("Nexus Agents")
            .setContentText("Herramientas activas — programando en segundo plano")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(NexusAgentsApp.NOTIFICATION_ID, notification)

        // Si el sistema mata el servicio, NO reiniciar automáticamente
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        if (::wakeLock.isInitialized && wakeLock.isHeld) {
            wakeLock.release()
        }
        super.onDestroy()
    }
}
