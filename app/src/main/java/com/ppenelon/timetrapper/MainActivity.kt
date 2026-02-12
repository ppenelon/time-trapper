package com.ppenelon.timetrapper

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.switchmaterial.SwitchMaterial
import com.ppenelon.timetrapper.accessibility.AppAccessibilityService
import com.ppenelon.timetrapper.storage.AppPreferences
import com.ppenelon.timetrapper.timer.AppTimerManager
import java.util.Locale

/**
 * Écran de configuration:
 * - permissions nécessaires
 * - apps surveillées
 * - sessions actives et logs debug
 */
class MainActivity : AppCompatActivity() {

    private data class KnownAppSuggestion(
        val title: String,
        val packageName: String
    )

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

    private lateinit var textAccessibilityStatus: TextView
    private lateinit var textOverlayStatus: TextView
    private lateinit var buttonOpenAccessibilitySettings: Button
    private lateinit var buttonOpenOverlaySettings: Button
    private lateinit var debugSwitch: SwitchMaterial
    private lateinit var textActiveSessions: TextView

    private val sessionsUpdater = object : Runnable {
        override fun run() {
            renderActiveSessions()
            uiHandler.postDelayed(this, 1_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        appPreferences = AppPreferences(applicationContext)
        AppTimerManager.initialize(applicationContext)

        bindViews()
        bindActions()
        debugSwitch.isChecked = appPreferences.isDebugLogsEnabled()
    }

    override fun onResume() {
        super.onResume()
        renderPermissionStatus()
        renderMonitoredPackages()
        renderActiveSessions()
        uiHandler.post(sessionsUpdater)
    }

    override fun onPause() {
        super.onPause()
        uiHandler.removeCallbacks(sessionsUpdater)
    }

    private fun bindViews() {
        textAccessibilityStatus = findViewById(R.id.textAccessibilityStatus)
        textOverlayStatus = findViewById(R.id.textOverlayStatus)
        buttonOpenAccessibilitySettings = findViewById(R.id.buttonOpenAccessibilitySettings)
        buttonOpenOverlaySettings = findViewById(R.id.buttonOpenOverlaySettings)
        debugSwitch = findViewById(R.id.switchDebugLogs)
        textActiveSessions = findViewById(R.id.textActiveSessions)
    }

    private fun bindActions() {
        buttonOpenAccessibilitySettings.setOnClickListener {
            openAccessibilitySettings()
        }

        buttonOpenOverlaySettings.setOnClickListener {
            openOverlaySettings()
        }

        findViewById<Button>(R.id.buttonAddMonitoredApp).setOnClickListener {
            showKnownAppsPickerDialog()
        }

        findViewById<Button>(R.id.buttonClearSessions).setOnClickListener {
            AppTimerManager.clearAllSessions()
            showToast(getString(R.string.toast_sessions_cleared))
            renderActiveSessions()
        }

        debugSwitch.setOnCheckedChangeListener { _, isEnabled ->
            appPreferences.setDebugLogsEnabled(isEnabled)
        }
    }

    private fun renderMonitoredPackages() {
        val monitoredAppContainer = findViewById<LinearLayout>(R.id.monitoredAppContainer)
        monitoredAppContainer.removeAllViews()

        val monitoredApps = appPreferences.getMonitoredApps()
        if (monitoredApps.isEmpty()) {
            monitoredAppContainer.addView(
                TextView(this).apply {
                    text = getString(R.string.no_monitored_apps)
                    setTextColor(ContextCompat.getColor(this@MainActivity, R.color.color_on_surface_muted))
                }
            )
            return
        }

        monitoredApps.forEach { monitoredApp ->
            monitoredAppContainer.addView(createMonitoredAppRow(monitoredApp, monitoredAppContainer))
        }
    }

    private fun createMonitoredAppRow(
        monitoredApp: AppPreferences.MonitoredApp,
        parentContainer: LinearLayout
    ): View {
        val rowView = LayoutInflater.from(this)
            .inflate(R.layout.item_monitored_app, parentContainer, false)

        val titleTextView = rowView.findViewById<TextView>(R.id.textAppTitle)
        val packageTextView = rowView.findViewById<TextView>(R.id.textAppPackage)
        val editButton = rowView.findViewById<Button>(R.id.buttonEditApp)
        val removeButton = rowView.findViewById<Button>(R.id.buttonRemoveApp)

        titleTextView.text = monitoredApp.title
        packageTextView.text = monitoredApp.packageName

        editButton.setOnClickListener {
            showMonitoredAppDialog(existingApp = monitoredApp)
        }

        removeButton.setOnClickListener {
            appPreferences.removeMonitoredApp(monitoredApp.packageName)
            showToast(getString(R.string.toast_package_removed))
            renderMonitoredPackages()
            renderActiveSessions()
        }

        return rowView
    }

    private fun renderPermissionStatus() {
        val cardPermissions = findViewById<View>(R.id.cardPermissions)
        val textPermissionPriorityTitle = findViewById<TextView>(R.id.textPermissionPriorityTitle)
        val textPermissionPriorityHint = findViewById<TextView>(R.id.textPermissionPriorityHint)

        val accessibilityEnabled = isAccessibilityServiceEnabled()
        val overlayEnabled = Settings.canDrawOverlays(this)
        val allReady = accessibilityEnabled && overlayEnabled

        textAccessibilityStatus.text = if (accessibilityEnabled) {
            getString(R.string.status_accessibility_enabled)
        } else {
            getString(R.string.status_accessibility_disabled)
        }

        textOverlayStatus.text = if (overlayEnabled) {
            getString(R.string.status_overlay_enabled)
        } else {
            getString(R.string.status_overlay_disabled)
        }

        if (allReady) {
            cardPermissions.setBackgroundResource(R.drawable.bg_permissions_ready)
            textPermissionPriorityTitle.text = getString(R.string.permissions_ready_title)
            textPermissionPriorityHint.text = getString(R.string.permissions_ready_hint)
            textPermissionPriorityHint.alpha = 0.75f
        } else {
            cardPermissions.setBackgroundResource(R.drawable.bg_permissions_priority)
            textPermissionPriorityTitle.text = getString(R.string.permissions_priority_title)
            textPermissionPriorityHint.text = getString(R.string.permissions_priority_hint)
            textPermissionPriorityHint.alpha = 1f
        }

        buttonOpenAccessibilitySettings.alpha = if (accessibilityEnabled) 0.65f else 1f
        buttonOpenOverlaySettings.alpha = if (overlayEnabled) 0.65f else 1f
    }

    private fun renderActiveSessions() {
        // Petit tableau de bord temps réel (bonus): montre les timers en cours.
        val sessions = AppTimerManager.getActiveSessions()
        if (sessions.isEmpty()) {
            textActiveSessions.text = getString(R.string.no_active_sessions)
            return
        }

        val now = System.currentTimeMillis()
        val rows = sessions.map { session ->
            val monitoredAppTitle = appPreferences.findMonitoredApp(session.packageName)?.title
                ?: session.packageName
            val appLabel = "$monitoredAppTitle (${session.packageName})"

            if (session.isUnlimited) {
                "$appLabel - ${getString(R.string.session_unlimited)}"
            } else {
                val remainingMillis = (session.expiresAtMillis ?: now) - now
                val remainingTime = formatTime(remainingMillis.coerceAtLeast(0L))
                "$appLabel - ${getString(R.string.session_remaining, remainingTime)}"
            }
        }

        textActiveSessions.text = rows.joinToString(separator = "\n")
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

    private fun showMonitoredAppDialog(existingApp: AppPreferences.MonitoredApp?) {
        try {
            val dialogView = LayoutInflater.from(this)
                .inflate(R.layout.dialog_edit_monitored_app, null)

            val editTitle = dialogView.findViewById<EditText>(R.id.editTextAppTitle)
            val editPackage = dialogView.findViewById<EditText>(R.id.editTextAppPackage)

            editTitle.setText(existingApp?.title.orEmpty())
            editPackage.setText(existingApp?.packageName.orEmpty())

            val dialogTitle = if (existingApp == null) {
                getString(R.string.dialog_add_app_title)
            } else {
                getString(R.string.dialog_edit_app_title)
            }

            val dialog = AlertDialog.Builder(this)
                .setTitle(dialogTitle)
                .setView(dialogView)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.dialog_save, null)
                .create()

            dialog.setOnShowListener {
                val saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                saveButton.setOnClickListener {
                    editTitle.error = null
                    editPackage.error = null

                    val title = editTitle.text?.toString()?.trim().orEmpty()
                    val packageName = editPackage.text?.toString()?.trim()?.lowercase(Locale.US).orEmpty()

                    var isValid = true
                    if (title.isBlank()) {
                        editTitle.error = getString(R.string.error_app_title_required)
                        isValid = false
                    }
                    if (!isValidPackageName(packageName)) {
                        editPackage.error = getString(R.string.error_app_package_invalid)
                        isValid = false
                    }
                    if (!isValid) {
                        return@setOnClickListener
                    }

                    appPreferences.upsertMonitoredApp(
                        title = title,
                        packageName = packageName,
                        previousPackageName = existingApp?.packageName
                    )

                    if (existingApp == null) {
                        showToast(getString(R.string.toast_package_added))
                    } else {
                        showToast(getString(R.string.toast_package_updated))
                    }

                    renderMonitoredPackages()
                    renderActiveSessions()
                    dialog.dismiss()
                }
            }

            dialog.show()
            dialog.window?.setBackgroundDrawableResource(R.drawable.bg_overlay_modal)
        } catch (_: Throwable) {
            showToast(getString(R.string.toast_dialog_open_error))
        }
    }

    private fun showKnownAppsPickerDialog() {
        val monitoredPackages = appPreferences.getMonitoredPackages()
        val availableSuggestions = knownAppSuggestions
            .filterNot { monitoredPackages.contains(it.packageName) }

        if (availableSuggestions.isEmpty()) {
            showMonitoredAppDialog(existingApp = null)
            return
        }

        val options = availableSuggestions
            .map { suggestion -> "${suggestion.title} (${suggestion.packageName})" }
            .toMutableList()
            .apply { add(getString(R.string.dialog_known_apps_custom_option)) }
            .toTypedArray()

        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_known_apps_title)
            .setItems(options) { dialog, index ->
                if (index == availableSuggestions.size) {
                    showMonitoredAppDialog(existingApp = null)
                } else {
                    val selectedSuggestion = availableSuggestions[index]
                    appPreferences.upsertMonitoredApp(
                        title = selectedSuggestion.title,
                        packageName = selectedSuggestion.packageName
                    )
                    showToast(getString(R.string.toast_package_added))
                    renderMonitoredPackages()
                    renderActiveSessions()
                }
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
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
