package com.malcolmocean.randoping

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat

object NotificationHelper {

    // Shared ID so each new ping replaces the previous, even across different ping types
    const val NOTIFICATION_ID = 1

    fun showNotification(context: Context, config: PingConfig) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val resolved = VibrationPatterns.resolve(config)

        // Delete and recreate channel so vibration settings take effect
        // (channel settings are sticky after first creation)
        notificationManager.deleteNotificationChannel(config.channelId)
        val channel = NotificationChannel(
            config.channelId,
            config.name,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Pings for ${config.name}"
            // Disable channel vibration — we handle it manually for amplitude control
            enableVibration(false)
        }
        notificationManager.createNotificationChannel(channel)

        // Vibrate manually so we can use amplitude control
        if (!resolved.isEmpty) {
            triggerVibration(context, resolved)
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
            .setVibrate(longArrayOf(0))
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun triggerVibration(context: Context, resolved: ResolvedVibration) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        val effect = if (resolved.amplitudes != null && vibrator.hasAmplitudeControl()) {
            VibrationEffect.createWaveform(resolved.timings, resolved.amplitudes, -1)
        } else {
            VibrationEffect.createWaveform(resolved.timings, -1)
        }
        vibrator.vibrate(effect)
    }
}
