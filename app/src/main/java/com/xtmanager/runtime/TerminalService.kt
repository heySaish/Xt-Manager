package com.xtmanager.runtime

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.xtmanager.MainActivity

class TerminalService : Service() {

    companion object {
        const val CHANNEL_ID = "xtmanager_terminal_service"
        const val NOTIFICATION_ID = 1001
        const val ACTION_EXIT = "com.xtmanager.ACTION_EXIT_TERMINAL"
        const val ACTION_WAKELOCK_TOGGLE = "com.xtmanager.ACTION_WAKELOCK_TOGGLE"
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private var isWakeLockAcquired = false
    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): TerminalService = this@TerminalService
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_EXIT -> {
                stopForeground(true)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_WAKELOCK_TOGGLE -> {
                if (isWakeLockAcquired) {
                    releaseWakeLock()
                } else {
                    acquireWakeLock()
                }
                updateNotification()
            }
        }

        startForeground(NOTIFICATION_ID, buildNotification())
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "XtManager::TerminalWakeLock"
            )
        }
        wakeLock?.let {
            if (!it.isHeld) {
                it.acquire()
                isWakeLockAcquired = true
            }
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                isWakeLockAcquired = false
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Xt-Manager Terminal Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps Alpine Linux Terminal session active"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun updateNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): android.app.Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val exitIntent = Intent(this, TerminalService::class.java).apply {
            action = ACTION_EXIT
        }
        val exitPendingIntent = PendingIntent.getService(
            this, 1, exitIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val wakelockIntent = Intent(this, TerminalService::class.java).apply {
            action = ACTION_WAKELOCK_TOGGLE
        }
        val wakelockPendingIntent = PendingIntent.getService(
            this, 2, wakelockIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val wakelockActionText = if (isWakeLockAcquired) "Release wakelock" else "Acquire wakelock"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Xt-Manager")
            .setContentText("1 session")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setShowWhen(false)
            .addAction(0, "Exit", exitPendingIntent)
            .addAction(0, wakelockActionText, wakelockPendingIntent)
            .build()
    }

    override fun onDestroy() {
        releaseWakeLock()
        super.onDestroy()
    }
}
