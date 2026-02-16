package com.malcolmocean.nowtice

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar
import kotlin.math.ln
import kotlin.random.Random

object PingScheduler {

    fun scheduleNextPing(context: Context, config: PingConfig) {
        if (!config.enabled) {
            cancelPing(context, config)
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, PingReceiver::class.java).apply {
            putExtra("ping_id", config.id)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            config.requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val delayMinutes = exponentialRandom(config.avgMinutes.toDouble())
        var nextPingTime = System.currentTimeMillis() + (delayMinutes * 60 * 1000).toLong()

        nextPingTime = adjustForQuietHours(nextPingTime, config)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    nextPingTime,
                    pendingIntent
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    nextPingTime,
                    pendingIntent
                )
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                nextPingTime,
                pendingIntent
            )
        }
    }

    fun cancelPing(context: Context, config: PingConfig) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, PingReceiver::class.java).apply {
            putExtra("ping_id", config.id)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            config.requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    fun scheduleAll(context: Context, configs: List<PingConfig>) {
        for (config in configs) {
            if (config.enabled) {
                scheduleNextPing(context, config)
            } else {
                cancelPing(context, config)
            }
        }
    }

    private fun exponentialRandom(mean: Double): Double {
        return -mean * ln(1 - Random.nextDouble())
    }

    private fun adjustForQuietHours(timeMillis: Long, config: PingConfig): Long {
        val calendar = Calendar.getInstance().apply { this.timeInMillis = timeMillis }
        val hour = calendar.get(Calendar.HOUR_OF_DAY)

        val inQuietHours = if (config.quietStartHour > config.quietEndHour) {
            hour >= config.quietStartHour || hour < config.quietEndHour
        } else {
            hour >= config.quietStartHour && hour < config.quietEndHour
        }

        if (inQuietHours) {
            calendar.set(Calendar.HOUR_OF_DAY, config.quietEndHour)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)

            if (hour >= config.quietStartHour) {
                calendar.add(Calendar.DAY_OF_YEAR, 1)
            }

            return calendar.timeInMillis
        }

        return timeMillis
    }
}
