package com.malcolmocean.randoping

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar
import kotlin.math.ln
import kotlin.random.Random

object PingScheduler {

    private const val PING_REQUEST_CODE = 1001

    /**
     * Schedule the next ping using exponential distribution (TagTime style).
     * The gap between pings follows an exponential distribution with mean = avgMinutes.
     */
    fun scheduleNextPing(context: Context, settings: PingSettings) {
        if (!settings.enabled) {
            cancelPing(context)
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, PingReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            PING_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Calculate next ping time using exponential distribution
        val delayMinutes = exponentialRandom(settings.avgMinutes.toDouble())
        var nextPingTime = System.currentTimeMillis() + (delayMinutes * 60 * 1000).toLong()

        // Adjust for quiet hours
        nextPingTime = adjustForQuietHours(nextPingTime, settings)

        // Schedule the alarm
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    nextPingTime,
                    pendingIntent
                )
            } else {
                // Fall back to inexact alarm
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

    fun cancelPing(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, PingReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            PING_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    /**
     * Exponential random variable with given mean.
     * This gives the "memoryless" property - the expected time to next ping
     * is always the same regardless of when the last ping was.
     */
    private fun exponentialRandom(mean: Double): Double {
        return -mean * ln(1 - Random.nextDouble())
    }

    /**
     * If the scheduled time falls within quiet hours, push it to after quiet hours end.
     */
    private fun adjustForQuietHours(timeMillis: Long, settings: PingSettings): Long {
        val calendar = Calendar.getInstance().apply { timeInMillis = timeMillis }
        val hour = calendar.get(Calendar.HOUR_OF_DAY)

        val inQuietHours = if (settings.quietStartHour > settings.quietEndHour) {
            // Quiet hours span midnight (e.g., 22:00 to 08:00)
            hour >= settings.quietStartHour || hour < settings.quietEndHour
        } else {
            // Quiet hours within same day (e.g., 14:00 to 16:00)
            hour >= settings.quietStartHour && hour < settings.quietEndHour
        }

        if (inQuietHours) {
            // Move to quiet end hour
            calendar.set(Calendar.HOUR_OF_DAY, settings.quietEndHour)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)

            // If we're past midnight but before quiet end, it's today
            // If we're before midnight but after quiet start, it's tomorrow
            if (hour >= settings.quietStartHour) {
                calendar.add(Calendar.DAY_OF_YEAR, 1)
            }

            return calendar.timeInMillis
        }

        return timeMillis
    }
}
