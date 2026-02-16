package com.malcolmocean.nowtice

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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
                NotificationHelper.showNotification(context, config)
            }

            PingScheduler.scheduleNextPing(context, config)
        }
    }
}
