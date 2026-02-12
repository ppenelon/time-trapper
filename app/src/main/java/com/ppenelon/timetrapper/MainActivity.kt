package com.ppenelon.timetrapper

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ppenelon.timetrapper.accessibility.AppAccessibilityService
import com.ppenelon.timetrapper.storage.AppPreferences
import com.ppenelon.timetrapper.timer.AppTimerManager
import com.ppenelon.timetrapper.ui.theme.TimeTrapperTheme
import java.util.Locale

private data class KnownAppSuggestion(
    val title: String,
    val packageName: String
)

private data class PermissionUiState(
    val accessibilityEnabled: Boolean = false,
    val overlayEnabled: Boolean = false
) {
    val allReady: Boolean
        get() = accessibilityEnabled && overlayEnabled
}

private data class SessionUiModel(
    val packageName: String,
    val appLabel: String,
    val sessionText: String,
    val isUnlimited: Boolean
)

private data class AppEditorUiState(
    val existingPackageName: String? = null,
    val title: String = "",
    val packageName: String = "",
    val titleError: String? = null,
    val packageError: String? = null
)

/**
 * Ecran de configuration principal en Jetpack Compose + Material 3.
 */
class MainActivity : ComponentActivity() {

    private val knownAppSuggestions = listOf(
        KnownAppSuggestion("Facebook", "com.facebook.katana"),
        KnownAppSuggestion("Instagram", "com.instagram.android"),
        KnownAppSuggestion("TikTok", "com.zhiliaoapp.musically"),
        KnownAppSuggestion("YouTube", "com.google.android.youtube"),
        KnownAppSuggestion("LinkedIn", "com.linkedin.android"),
        KnownAppSuggestion("Snapchat", "com.snapchat.android"),
        KnownAppSuggestion("Pinterest", "com.pinterest"),
        KnownAppSuggestion("Reddit", "com.reddit.frontpage"),
        KnownAppSuggestion("X (Twitter)", "com.twitter.android"),
        KnownAppSuggestion("Threads (Meta)", "com.instagram.threads")
    )

    private lateinit var appPreferences: AppPreferences
    private val uiHandler = Handler(Looper.getMainLooper())

    private var permissionUiState by mutableStateOf(PermissionUiState())
    private var monitoredApps by mutableStateOf<List<AppPreferences.MonitoredApp>>(emptyList())
    private var activeSessions by mutableStateOf<List<SessionUiModel>>(emptyList())
    private var debugLogsEnabled by mutableStateOf(false)

    private var showKnownAppsDialog by mutableStateOf(false)
    private var appEditorState by mutableStateOf<AppEditorUiState?>(null)

    private val sessionsUpdater = object : Runnable {
        override fun run() {
            refreshActiveSessions()
            uiHandler.postDelayed(this, 1_000L)
        }
    }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        appPreferences = AppPreferences(applicationContext)
        AppTimerManager.initialize(applicationContext)
        debugLogsEnabled = appPreferences.isDebugLogsEnabled()

        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            TimeTrapperTheme {
                MainScreen(
                    windowSizeClass = windowSizeClass,
                    permissionUiState = permissionUiState,
                    monitoredApps = monitoredApps,
                    activeSessions = activeSessions,
                    debugLogsEnabled = debugLogsEnabled,
                    onOpenAccessibilitySettings = ::openAccessibilitySettings,
                    onOpenOverlaySettings = ::openOverlaySettings,
                    onAddMonitoredApp = ::onAddMonitoredApp,
                    onEditMonitoredApp = ::onEditMonitoredApp,
                    onRemoveMonitoredApp = ::removeMonitoredApp,
                    onDebugLogsChanged = ::onDebugLogsChanged,
                    onClearSessions = ::clearSessions
                )

                val availableKnownApps = remember(monitoredApps) {
                    getAvailableKnownSuggestions()
                }

                if (showKnownAppsDialog) {
                    KnownAppsDialog(
                        suggestions = availableKnownApps,
                        onDismiss = { showKnownAppsDialog = false },
                        onSuggestionSelected = { suggestion ->
                            showKnownAppsDialog = false
                            addKnownSuggestion(suggestion)
                        },
                        onCustomAppSelected = {
                            showKnownAppsDialog = false
                            openCustomEditor()
                        }
                    )
                }

                appEditorState?.let { editorState ->
                    EditMonitoredAppDialog(
                        state = editorState,
                        onDismiss = { appEditorState = null },
                        onTitleChanged = { value ->
                            appEditorState = editorState.copy(
                                title = value,
                                titleError = null
                            )
                        },
                        onPackageChanged = { value ->
                            appEditorState = editorState.copy(
                                packageName = value,
                                packageError = null
                            )
                        },
                        onSave = ::saveEditor
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionStatus()
        refreshMonitoredApps()
        refreshActiveSessions()
        uiHandler.post(sessionsUpdater)
    }

    override fun onPause() {
        super.onPause()
        uiHandler.removeCallbacks(sessionsUpdater)
    }

    private fun refreshPermissionStatus() {
        permissionUiState = PermissionUiState(
            accessibilityEnabled = isAccessibilityServiceEnabled(),
            overlayEnabled = Settings.canDrawOverlays(this)
        )
    }

    private fun refreshMonitoredApps() {
        monitoredApps = appPreferences.getMonitoredApps()
    }

    private fun refreshActiveSessions() {
        val sessions = AppTimerManager.getActiveSessions()
        if (sessions.isEmpty()) {
            activeSessions = emptyList()
            return
        }

        val now = System.currentTimeMillis()
        val monitoredByPackage = monitoredApps.associateBy { it.packageName }

        activeSessions = sessions.map { session ->
            val appTitle = monitoredByPackage[session.packageName]?.title ?: session.packageName
            val appLabel = "$appTitle (${session.packageName})"
            val sessionText = if (session.isUnlimited) {
                getString(R.string.session_unlimited)
            } else {
                val remainingMillis = (session.expiresAtMillis ?: now) - now
                val remainingTime = formatTime(remainingMillis.coerceAtLeast(0L))
                getString(R.string.session_remaining, remainingTime)
            }

            SessionUiModel(
                packageName = session.packageName,
                appLabel = appLabel,
                sessionText = sessionText,
                isUnlimited = session.isUnlimited
            )
        }
    }

    private fun onDebugLogsChanged(enabled: Boolean) {
        appPreferences.setDebugLogsEnabled(enabled)
        debugLogsEnabled = enabled
    }

    private fun onAddMonitoredApp() {
        val availableSuggestions = getAvailableKnownSuggestions()
        if (availableSuggestions.isEmpty()) {
            openCustomEditor()
            return
        }
        showKnownAppsDialog = true
    }

    private fun onEditMonitoredApp(app: AppPreferences.MonitoredApp) {
        appEditorState = AppEditorUiState(
            existingPackageName = app.packageName,
            title = app.title,
            packageName = app.packageName
        )
    }

    private fun openCustomEditor() {
        appEditorState = AppEditorUiState()
    }

    private fun addKnownSuggestion(suggestion: KnownAppSuggestion) {
        appPreferences.upsertMonitoredApp(
            title = suggestion.title,
            packageName = suggestion.packageName
        )
        showToast(getString(R.string.toast_package_added))
        refreshMonitoredApps()
        refreshActiveSessions()
    }

    private fun saveEditor() {
        val editorState = appEditorState ?: return

        val title = editorState.title.trim()
        val packageName = editorState.packageName.trim().lowercase(Locale.US)

        var titleError: String? = null
        var packageError: String? = null

        if (title.isBlank()) {
            titleError = getString(R.string.error_app_title_required)
        }
        if (!isValidPackageName(packageName)) {
            packageError = getString(R.string.error_app_package_invalid)
        }

        if (titleError != null || packageError != null) {
            appEditorState = editorState.copy(
                titleError = titleError,
                packageError = packageError
            )
            return
        }

        appPreferences.upsertMonitoredApp(
            title = title,
            packageName = packageName,
            previousPackageName = editorState.existingPackageName
        )

        val message = if (editorState.existingPackageName == null) {
            getString(R.string.toast_package_added)
        } else {
            getString(R.string.toast_package_updated)
        }

        showToast(message)
        appEditorState = null
        refreshMonitoredApps()
        refreshActiveSessions()
    }

    private fun removeMonitoredApp(app: AppPreferences.MonitoredApp) {
        appPreferences.removeMonitoredApp(app.packageName)
        showToast(getString(R.string.toast_package_removed))
        refreshMonitoredApps()
        refreshActiveSessions()
    }

    private fun clearSessions() {
        AppTimerManager.clearAllSessions()
        showToast(getString(R.string.toast_sessions_cleared))
        refreshActiveSessions()
    }

    private fun getAvailableKnownSuggestions(): List<KnownAppSuggestion> {
        val monitoredPackages = monitoredApps.map { it.packageName }.toSet()
        return knownAppSuggestions.filterNot { monitoredPackages.contains(it.packageName) }
    }

    private fun openAccessibilitySettings() {
        try {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (_: ActivityNotFoundException) {
            showToast(getString(R.string.toast_settings_open_error))
        }
    }

    private fun openOverlaySettings() {
        if (Settings.canDrawOverlays(this)) {
            showToast(getString(R.string.toast_overlay_already_enabled))
            return
        }

        try {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            showToast(getString(R.string.toast_settings_open_error))
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val accessibilityEnabled = Settings.Secure.getInt(
            contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED,
            0
        ) == 1
        if (!accessibilityEnabled) return false

        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        val expectedService = ComponentName(this, AppAccessibilityService::class.java).flattenToString()
        return enabledServices.split(':').any { enabledService ->
            enabledService.equals(expectedService, ignoreCase = true)
        }
    }

    private fun formatTime(millis: Long): String {
        val totalSeconds = millis / 1_000L
        val hours = totalSeconds / 3_600L
        val minutes = (totalSeconds % 3_600L) / 60L
        val seconds = totalSeconds % 60L

        return if (hours > 0) {
            String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    private fun isValidPackageName(packageName: String): Boolean {
        if (packageName.isBlank()) return false
        return Regex("^[a-zA-Z0-9_]+(\\.[a-zA-Z0-9_]+)+$").matches(packageName)
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(
    windowSizeClass: WindowSizeClass,
    permissionUiState: PermissionUiState,
    monitoredApps: List<AppPreferences.MonitoredApp>,
    activeSessions: List<SessionUiModel>,
    debugLogsEnabled: Boolean,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onAddMonitoredApp: () -> Unit,
    onEditMonitoredApp: (AppPreferences.MonitoredApp) -> Unit,
    onRemoveMonitoredApp: (AppPreferences.MonitoredApp) -> Unit,
    onDebugLogsChanged: (Boolean) -> Unit,
    onClearSessions: () -> Unit
) {
    val isCompact = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Compact

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(text = stringResource(id = R.string.app_name)) }
            )
        }
    ) { innerPadding ->
        if (isCompact) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    IntroSection()
                }
                item {
                    PermissionCard(
                        state = permissionUiState,
                        onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                        onOpenOverlaySettings = onOpenOverlaySettings
                    )
                }
                item {
                    MonitoredAppsCard(
                        monitoredApps = monitoredApps,
                        onAddMonitoredApp = onAddMonitoredApp,
                        onEditMonitoredApp = onEditMonitoredApp,
                        onRemoveMonitoredApp = onRemoveMonitoredApp
                    )
                }
                item {
                    SessionsCard(
                        activeSessions = activeSessions,
                        debugLogsEnabled = debugLogsEnabled,
                        onDebugLogsChanged = onDebugLogsChanged,
                        onClearSessions = onClearSessions
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                IntroSection()

                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        item {
                            PermissionCard(
                                state = permissionUiState,
                                onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                                onOpenOverlaySettings = onOpenOverlaySettings
                            )
                        }
                        item {
                            MonitoredAppsCard(
                                monitoredApps = monitoredApps,
                                onAddMonitoredApp = onAddMonitoredApp,
                                onEditMonitoredApp = onEditMonitoredApp,
                                onRemoveMonitoredApp = onRemoveMonitoredApp
                            )
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        item {
                            SessionsCard(
                                activeSessions = activeSessions,
                                debugLogsEnabled = debugLogsEnabled,
                                onDebugLogsChanged = onDebugLogsChanged,
                                onClearSessions = onClearSessions
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IntroSection() {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            Color.Transparent
                        )
                    )
                )
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(id = R.string.main_intro),
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = stringResource(id = R.string.main_wellbeing_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PermissionCard(
    state: PermissionUiState,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit
) {
    val backgroundColor = if (state.allReady) {
        MaterialTheme.colorScheme.tertiaryContainer
    } else {
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
    }

    val title = if (state.allReady) {
        stringResource(id = R.string.permissions_ready_title)
    } else {
        stringResource(id = R.string.permissions_priority_title)
    }

    val hint = if (state.allReady) {
        stringResource(id = R.string.permissions_ready_hint)
    } else {
        stringResource(id = R.string.permissions_priority_hint)
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = backgroundColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = hint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            PermissionRow(
                statusText = stringResource(id = R.string.status_accessibility_enabled)
                    .takeIf { state.accessibilityEnabled }
                    ?: stringResource(id = R.string.status_accessibility_disabled),
                actionText = stringResource(id = R.string.button_open_accessibility_settings),
                isEnabled = state.accessibilityEnabled,
                onActionClick = onOpenAccessibilitySettings
            )

            PermissionRow(
                statusText = stringResource(id = R.string.status_overlay_enabled)
                    .takeIf { state.overlayEnabled }
                    ?: stringResource(id = R.string.status_overlay_disabled),
                actionText = stringResource(id = R.string.button_open_overlay_settings),
                isEnabled = state.overlayEnabled,
                onActionClick = onOpenOverlaySettings
            )
        }
    }
}

@Composable
private fun PermissionRow(
    statusText: String,
    actionText: String,
    isEnabled: Boolean,
    onActionClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = if (isEnabled) Icons.Rounded.CheckCircle else Icons.Rounded.WarningAmber,
                contentDescription = null,
                tint = if (isEnabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                }
            )
            Text(
                text = statusText,
                style = MaterialTheme.typography.titleMedium
            )
        }

        OutlinedButton(
            onClick = onActionClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = actionText)
        }
    }
}

@Composable
private fun MonitoredAppsCard(
    monitoredApps: List<AppPreferences.MonitoredApp>,
    onAddMonitoredApp: () -> Unit,
    onEditMonitoredApp: (AppPreferences.MonitoredApp) -> Unit,
    onRemoveMonitoredApp: (AppPreferences.MonitoredApp) -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(id = R.string.monitored_apps_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                FilledTonalButton(onClick = onAddMonitoredApp) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(id = R.string.button_add_monitored_app))
                }
            }

            Text(
                text = stringResource(id = R.string.monitored_apps_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (monitoredApps.isEmpty()) {
                Text(
                    text = stringResource(id = R.string.no_monitored_apps),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    monitoredApps.forEach { monitoredApp ->
                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.elevatedCardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = monitoredApp.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = monitoredApp.packageName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row {
                                    TextButton(onClick = { onEditMonitoredApp(monitoredApp) }) {
                                        Icon(
                                            imageVector = Icons.Rounded.Edit,
                                            contentDescription = null
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(text = stringResource(id = R.string.button_edit))
                                    }
                                    TextButton(
                                        onClick = { onRemoveMonitoredApp(monitoredApp) },
                                        colors = ButtonDefaults.textButtonColors(
                                            contentColor = MaterialTheme.colorScheme.error
                                        )
                                    ) {
                                        Text(text = stringResource(id = R.string.button_remove))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionsCard(
    activeSessions: List<SessionUiModel>,
    debugLogsEnabled: Boolean,
    onDebugLogsChanged: (Boolean) -> Unit,
    onClearSessions: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(id = R.string.active_sessions_title),
                style = MaterialTheme.typography.titleLarge
            )

            if (activeSessions.isEmpty()) {
                Text(
                    text = stringResource(id = R.string.no_active_sessions),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    activeSessions.forEach { session ->
                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.elevatedCardColors(
                                containerColor = if (session.isUnlimited) {
                                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                                } else {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                }
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = session.appLabel,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = session.sessionText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(id = R.string.debug_logs_label),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(id = R.string.debug_logs_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = debugLogsEnabled,
                    onCheckedChange = onDebugLogsChanged
                )
            }

            OutlinedButton(
                onClick = onClearSessions,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Rounded.Alarm,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(id = R.string.button_clear_sessions))
            }
        }
    }
}

@Composable
private fun KnownAppsDialog(
    suggestions: List<KnownAppSuggestion>,
    onDismiss: () -> Unit,
    onSuggestionSelected: (KnownAppSuggestion) -> Unit,
    onCustomAppSelected: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(id = R.string.dialog_known_apps_title))
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                suggestions.forEach { suggestion ->
                    OutlinedButton(
                        onClick = { onSuggestionSelected(suggestion) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "${suggestion.title} (${suggestion.packageName})")
                    }
                }

                FilledTonalButton(
                    onClick = onCustomAppSelected,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = stringResource(id = R.string.dialog_known_apps_custom_option))
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(id = android.R.string.cancel))
            }
        }
    )
}

@Composable
private fun EditMonitoredAppDialog(
    state: AppEditorUiState,
    onDismiss: () -> Unit,
    onTitleChanged: (String) -> Unit,
    onPackageChanged: (String) -> Unit,
    onSave: () -> Unit
) {
    val dialogTitle = if (state.existingPackageName == null) {
        stringResource(id = R.string.dialog_add_app_title)
    } else {
        stringResource(id = R.string.dialog_edit_app_title)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = dialogTitle) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = state.title,
                    onValueChange = onTitleChanged,
                    label = { Text(text = stringResource(id = R.string.dialog_app_title_hint)) },
                    singleLine = true,
                    isError = state.titleError != null,
                    supportingText = {
                        state.titleError?.let { Text(text = it) }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = state.packageName,
                    onValueChange = onPackageChanged,
                    label = { Text(text = stringResource(id = R.string.dialog_app_package_hint)) },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onDone = { onSave() }
                    ),
                    singleLine = true,
                    isError = state.packageError != null,
                    supportingText = {
                        state.packageError?.let { Text(text = it) }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onSave) {
                Text(text = stringResource(id = R.string.dialog_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(id = android.R.string.cancel))
            }
        }
    )
}
