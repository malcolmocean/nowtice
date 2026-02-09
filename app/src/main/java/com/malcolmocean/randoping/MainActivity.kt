package com.malcolmocean.randoping

import android.Manifest
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.Color
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
            scheduleAll()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsRepo = SettingsRepository(this)

        lifecycleScope.launch {
            settingsRepo.ensureMigrated()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        scheduleAll()

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MultiPingScreen(
                        settingsRepo = settingsRepo,
                        onConfigChanged = { config ->
                            PingScheduler.scheduleNextPing(this@MainActivity, config)
                        },
                        onConfigDeleted = { config ->
                            PingScheduler.cancelPing(this@MainActivity, config)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                val nm = getSystemService(NotificationManager::class.java)
                                nm.deleteNotificationChannel(config.channelId)
                            }
                        },
                        onConfigAdded = { config ->
                            PingScheduler.scheduleNextPing(this@MainActivity, config)
                        }
                    )
                }
            }
        }
    }

    private fun scheduleAll() {
        lifecycleScope.launch {
            val configs = settingsRepo.pingConfigs.first()
            PingScheduler.scheduleAll(this@MainActivity, configs)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiPingScreen(
    settingsRepo: SettingsRepository,
    onConfigChanged: (PingConfig) -> Unit,
    onConfigDeleted: (PingConfig) -> Unit,
    onConfigAdded: (PingConfig) -> Unit
) {
    val configs by settingsRepo.pingConfigs.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    // Keep selected tab in bounds
    LaunchedEffect(configs.size) {
        if (selectedTabIndex >= configs.size && configs.isNotEmpty()) {
            selectedTabIndex = configs.size - 1
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "RandoPing",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(16.dp)
        )

        if (configs.isEmpty()) {
            EmptyState(
                onAdd = {
                    scope.launch {
                        val newConfig = PingConfig()
                        settingsRepo.addConfig(newConfig)
                        onConfigAdded(newConfig)
                        selectedTabIndex = 0
                    }
                }
            )
        } else {
            PingTabRow(
                configs = configs,
                selectedTabIndex = selectedTabIndex,
                onTabSelected = { selectedTabIndex = it },
                onAddPing = {
                    scope.launch {
                        val lastConfig = configs.lastOrNull()
                        val newConfig = PingConfig(
                            name = "Ping ${configs.size + 1}!",
                            avgMinutes = lastConfig?.avgMinutes ?: 45,
                            quietStartHour = lastConfig?.quietStartHour ?: 22,
                            quietEndHour = lastConfig?.quietEndHour ?: 8,
                            colorValue = PingColors.nextUnusedColor(configs)
                        )
                        settingsRepo.addConfig(newConfig)
                        onConfigAdded(newConfig)
                        selectedTabIndex = configs.size // will be the new last index
                    }
                }
            )

            if (selectedTabIndex < configs.size) {
                PingConfigEditor(
                    config = configs[selectedTabIndex],
                    onUpdate = { updated ->
                        scope.launch {
                            settingsRepo.updateConfig(updated)
                            onConfigChanged(updated)
                        }
                    },
                    onDelete = {
                        scope.launch {
                            val config = configs[selectedTabIndex]
                            settingsRepo.removeConfig(config.id)
                            onConfigDeleted(config)
                            if (selectedTabIndex > 0) {
                                selectedTabIndex--
                            }
                        }
                    },
                    onIntervalChangeFinished = { config ->
                        onConfigChanged(config)
                    }
                )
            }
        }
    }
}

@Composable
fun PingTabRow(
    configs: List<PingConfig>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    onAddPing: () -> Unit
) {
    ScrollableTabRow(
        selectedTabIndex = selectedTabIndex.coerceIn(0, configs.size),
        edgePadding = 8.dp
    ) {
        configs.forEachIndexed { index, config ->
            val iconEntry = PingIcons.findByKey(config.iconName) ?: PingIcons.default
            Tab(
                selected = selectedTabIndex == index,
                onClick = { onTabSelected(index) },
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = iconEntry.composeIcon,
                            contentDescription = null,
                            tint = PingColors.toComposeColor(config.colorValue),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = config.name,
                            color = if (selectedTabIndex == index)
                                PingColors.toComposeColor(config.colorValue)
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        }
        Tab(
            selected = false,
            onClick = onAddPing,
            text = {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Add ping",
                    modifier = Modifier.size(18.dp)
                )
            }
        )
    }
}

@Composable
fun PingConfigEditor(
    config: PingConfig,
    onUpdate: (PingConfig) -> Unit,
    onDelete: () -> Unit,
    onIntervalChangeFinished: (PingConfig) -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    // Local state to avoid DataStore round-trip on every keystroke
    var sliderValue by remember(config.id) { mutableFloatStateOf(config.avgMinutes.toFloat()) }
    var nameText by remember(config.id) { mutableStateOf(config.name) }
    var messageText by remember(config.id) { mutableStateOf(config.message) }
    var quietStartText by remember(config.id) { mutableStateOf(config.quietStartHour.toString()) }
    var quietEndText by remember(config.id) { mutableStateOf(config.quietEndHour.toString()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Enabled toggle
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
                    checked = config.enabled,
                    onCheckedChange = { onUpdate(config.copy(enabled = it)) }
                )
            }
        }

        // Title
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Title", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = nameText,
                    onValueChange = { nameText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { if (!it.isFocused) onUpdate(config.copy(name = nameText)) },
                    singleLine = true
                )
            }
        }

        // Message
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Notification message", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { if (!it.isFocused) onUpdate(config.copy(message = messageText)) },
                    minLines = 2
                )
            }
        }

        // Average interval
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Average interval", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "${sliderValue.toInt()} minutes",
                    style = MaterialTheme.typography.bodyLarge
                )
                Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    onValueChangeFinished = {
                        val updated = config.copy(avgMinutes = sliderValue.toInt())
                        onUpdate(updated)
                        onIntervalChangeFinished(updated)
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
                            value = quietStartText,
                            onValueChange = { quietStartText = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged {
                                    if (!it.isFocused) {
                                        val hour = quietStartText.toIntOrNull()?.coerceIn(0, 23)
                                        if (hour != null) {
                                            quietStartText = hour.toString()
                                            onUpdate(config.copy(quietStartHour = hour))
                                        } else {
                                            quietStartText = config.quietStartHour.toString()
                                        }
                                    }
                                },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            suffix = { Text(":00") }
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("End", style = MaterialTheme.typography.bodyMedium)
                        OutlinedTextField(
                            value = quietEndText,
                            onValueChange = { quietEndText = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged {
                                    if (!it.isFocused) {
                                        val hour = quietEndText.toIntOrNull()?.coerceIn(0, 23)
                                        if (hour != null) {
                                            quietEndText = hour.toString()
                                            onUpdate(config.copy(quietEndHour = hour))
                                        } else {
                                            quietEndText = config.quietEndHour.toString()
                                        }
                                    }
                                },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            suffix = { Text(":00") }
                        )
                    }
                }
                Text(
                    "No pings between ${config.quietStartHour}:00 and ${config.quietEndHour}:00",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Color picker
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Color", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    PingColors.all.forEach { colorValue ->
                        val isSelected = config.colorValue == colorValue
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(PingColors.toComposeColor(colorValue))
                                .then(
                                    if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                    else Modifier
                                )
                                .clickable { onUpdate(config.copy(colorValue = colorValue)) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = "Selected",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Icon picker
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Icon", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(6),
                    modifier = Modifier.height(240.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(PingIcons.all) { iconEntry ->
                        val isSelected = config.iconName == iconEntry.key
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) PingColors.toComposeColor(config.colorValue).copy(alpha = 0.2f)
                                    else Color.Transparent
                                )
                                .then(
                                    if (isSelected) Modifier.border(2.dp, PingColors.toComposeColor(config.colorValue), CircleShape)
                                    else Modifier
                                )
                                .clickable { onUpdate(config.copy(iconName = iconEntry.key)) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = iconEntry.composeIcon,
                                contentDescription = iconEntry.label,
                                tint = if (isSelected) PingColors.toComposeColor(config.colorValue)
                                       else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }

        // Delete button
        OutlinedButton(
            onClick = { showDeleteDialog = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            )
        ) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Delete this ping")
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete ${config.name}?") },
            text = { Text("This will stop all future pings for this configuration.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun EmptyState(onAdd: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "No pings configured",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FloatingActionButton(onClick = onAdd) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Add ping"
                )
            }
        }
    }
}
