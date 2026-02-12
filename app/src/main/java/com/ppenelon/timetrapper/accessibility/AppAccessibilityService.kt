package com.ppenelon.timetrapper.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.ppenelon.timetrapper.overlay.OverlayManager
import com.ppenelon.timetrapper.storage.AppPreferences
import com.ppenelon.timetrapper.timer.AppTimerManager
import com.ppenelon.timetrapper.ui.BlockedActivity

/**
 * Service central:
 * - détecte l'app au premier plan
 * - déclenche l'overlay de choix
 * - applique le blocage à expiration
 */
class AppAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "AppAccessibilityService"
        private const val BLOCK_DEBOUNCE_MS = 1_500L
    }

    private lateinit var appPreferences: AppPreferences
    private lateinit var overlayManager: OverlayManager

    private var currentForegroundPackage: String? = null
    private var lastBlockedPackage: String? = null
    private var lastBlockedAtMillis: Long = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        appPreferences = AppPreferences(applicationContext)
        overlayManager = OverlayManager(applicationContext)

        AppTimerManager.initialize(applicationContext)
        AppTimerManager.setExpirationListener(::onSessionExpired)

        logDebug("Accessibility service connected.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!::appPreferences.isInitialized || !::overlayManager.isInitialized) {
            return
        }
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return
        }
        val packageName = event.packageName?.toString()?.trim().orEmpty()
        if (packageName.isEmpty()) return

        handleForegroundPackage(packageName)
    }

    override fun onInterrupt() {
        logDebug("Accessibility service interrupted.")
    }

    override fun onDestroy() {
        if (AppTimerManager.isReady()) {
            AppTimerManager.setExpirationListener(null)
        }
        if (::overlayManager.isInitialized) {
            overlayManager.hide()
        }
        super.onDestroy()
    }

    private fun handleForegroundPackage(packageName: String) {
        val previousPackage = currentForegroundPackage
        currentForegroundPackage = packageName
        // Session "sans limite" = valable uniquement tant que l'app reste au premier plan.
        AppTimerManager.onForegroundAppChanged(previousPackage, packageName)

        if (packageName == this.packageName) {
            return
        }

        val monitoredApp = appPreferences.findMonitoredApp(packageName)
        if (monitoredApp == null) {
            return
        }

        val existingSession = AppTimerManager.getSession(packageName)
        if (existingSession == null) {
            showTimeSelectionOverlay(monitoredApp)
            return
        }

        val isExpired = !existingSession.isUnlimited &&
            (existingSession.expiresAtMillis ?: 0L) <= System.currentTimeMillis()
        if (isExpired) {
            AppTimerManager.clearSession(packageName)
            enforceBlock(packageName)
        }
    }

    private fun showTimeSelectionOverlay(monitoredApp: AppPreferences.MonitoredApp) {
        val packageName = monitoredApp.packageName
        if (overlayManager.isShowing()) {
            return
        }
        if (!overlayManager.canDisplayOverlay()) {
            logDebug("Overlay permission missing; cannot ask time for $packageName.")
            return
        }

        val appLabel = "${monitoredApp.title} (${monitoredApp.packageName})"
        val displayed = overlayManager.showTimePicker(packageName, appLabel) { selection ->
            if (selection.minutes == null) {
                AppTimerManager.startUnlimitedSession(packageName)
                logDebug("Unlimited session granted for $packageName.")
            } else {
                AppTimerManager.startTimedSession(packageName, selection.minutes)
                logDebug("Timer started for $packageName (${selection.minutes} min).")
            }
        }

        if (!displayed) {
            logDebug("Failed to display overlay for $packageName.")
        }
    }

    private fun onSessionExpired(packageName: String) {
        logDebug("Session expired for $packageName.")
        if (currentForegroundPackage == packageName) {
            enforceBlock(packageName)
        }
    }

    private fun enforceBlock(packageName: String) {
        val now = System.currentTimeMillis()
        if (lastBlockedPackage == packageName && now - lastBlockedAtMillis < BLOCK_DEBOUNCE_MS) {
            return
        }

        lastBlockedPackage = packageName
        lastBlockedAtMillis = now
        overlayManager.hide()

        val monitoredTitle = appPreferences.findMonitoredApp(packageName)?.title ?: packageName
        val appLabel = "$monitoredTitle ($packageName)"

        val shownAsOverlay = overlayManager.showBlockedOverlay(
            packageName = packageName,
            appDisplayName = appLabel,
            onExtendFiveMinutes = {
                AppTimerManager.extendSession(packageName, 5)
                logDebug("Session extended from blocked overlay for $packageName.")
            },
            onGoHome = {
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
        )

        if (shownAsOverlay) {
            logDebug("Blocked overlay displayed for $packageName.")
            return
        }

        performGlobalAction(GLOBAL_ACTION_HOME)
        launchBlockedActivityFallback(packageName)
    }

    private fun launchBlockedActivityFallback(packageName: String) {
        val blockedIntent = Intent(this, BlockedActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
            putExtra(BlockedActivity.EXTRA_PACKAGE_NAME, packageName)
        }
        startActivity(blockedIntent)
        logDebug("Blocked activity fallback displayed for $packageName.")
    }

    private fun logDebug(message: String) {
        if (::appPreferences.isInitialized && appPreferences.isDebugLogsEnabled()) {
            Log.d(TAG, message)
        }
    }
}
