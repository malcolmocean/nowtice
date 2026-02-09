package com.malcolmocean.randoping

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            CoroutineScope(Dispatchers.IO).launch {
                val repo = SettingsRepository(context)
                repo.ensureMigrated()
                val configs = repo.pingConfigs.first()
                PingScheduler.scheduleAll(context, configs)
            }
        }
    }
}
