package com.malcolmocean.randoping

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PingReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pingId = intent.getStringExtra("ping_id") ?: return

        CoroutineScope(Dispatchers.IO).launch {
            val repo = SettingsRepository(context)
            val configs = repo.pingConfigs.first()
            val config = configs.find { it.id == pingId } ?: return@launch

            if (config.enabled) {
                showNotification(context, config)
            }

            PingScheduler.scheduleNextPing(context, config)
        }
    }

    private fun showNotification(context: Context, config: PingConfig) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                config.channelId,
                config.name,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Pings for ${config.name}"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            config.requestCode,
            tapIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val iconEntry = PingIcons.findByKey(config.iconName) ?: PingIcons.default

        val notification = NotificationCompat.Builder(context, config.channelId)
            .setSmallIcon(iconEntry.drawableResId)
            .setContentTitle(config.name)
            .setContentText(config.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(config.message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setColor(config.colorValue.toInt())
            .setVibrate(longArrayOf(0, 250, 250, 250))
            .build()

        notificationManager.notify(config.notificationId, notification)
    }
}
