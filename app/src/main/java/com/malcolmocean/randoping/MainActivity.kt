package com.malcolmocean.randoping

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var settingsRepo: SettingsRepository

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            scheduleIfEnabled()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsRepo = SettingsRepository(this)

        // Request notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Schedule initial ping
        scheduleIfEnabled()

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SettingsScreen(settingsRepo) { settings ->
                        PingScheduler.scheduleNextPing(this@MainActivity, settings)
                    }
                }
            }
        }
    }

    private fun scheduleIfEnabled() {
        lifecycleScope.launch {
            val settings = settingsRepo.settings.first()
            if (settings.enabled) {
                PingScheduler.scheduleNextPing(this@MainActivity, settings)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsRepo: SettingsRepository,
    onSettingsChanged: (PingSettings) -> Unit
) {
    val settings by settingsRepo.settings.collectAsState(initial = PingSettings())
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(
            text = "RandoPing",
            style = MaterialTheme.typography.headlineLarge
        )

        // Enable/Disable toggle
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Enabled", style = MaterialTheme.typography.titleMedium)
                Switch(
                    checked = settings.enabled,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            settingsRepo.updateEnabled(enabled)
                            val newSettings = settings.copy(enabled = enabled)
                            onSettingsChanged(newSettings)
                        }
                    }
                )
            }
        }

        // Average interval
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Average interval", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "${settings.avgMinutes} minutes",
                    style = MaterialTheme.typography.bodyLarge
                )
                Slider(
                    value = settings.avgMinutes.toFloat(),
                    onValueChange = { value ->
                        scope.launch {
                            settingsRepo.updateAvgMinutes(value.toInt())
                        }
                    },
                    onValueChangeFinished = {
                        scope.launch {
                            onSettingsChanged(settings)
                        }
                    },
                    valueRange = 5f..120f,
                    steps = 22
                )
                Text(
                    "Pings follow an exponential distribution around this average",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Quiet hours
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Quiet hours", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Start", style = MaterialTheme.typography.bodyMedium)
                        OutlinedTextField(
                            value = settings.quietStartHour.toString(),
                            onValueChange = { value ->
                                val hour = value.toIntOrNull()?.coerceIn(0, 23) ?: return@OutlinedTextField
                                scope.launch {
                                    settingsRepo.updateQuietHours(hour, settings.quietEndHour)
                                    onSettingsChanged(settings.copy(quietStartHour = hour))
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            suffix = { Text(":00") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("End", style = MaterialTheme.typography.bodyMedium)
                        OutlinedTextField(
                            value = settings.quietEndHour.toString(),
                            onValueChange = { value ->
                                val hour = value.toIntOrNull()?.coerceIn(0, 23) ?: return@OutlinedTextField
                                scope.launch {
                                    settingsRepo.updateQuietHours(settings.quietStartHour, hour)
                                    onSettingsChanged(settings.copy(quietEndHour = hour))
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            suffix = { Text(":00") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                Text(
                    "No pings between ${settings.quietStartHour}:00 and ${settings.quietEndHour}:00",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Message
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Notification message", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = settings.message,
                    onValueChange = { value ->
                        scope.launch {
                            settingsRepo.updateMessage(value)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
