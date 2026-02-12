package com.ppenelon.timetrapper.timer

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.ppenelon.timetrapper.storage.AppPreferences

/**
 * Registre global des sessions par app + compte à rebours persistant en SharedPreferences.
 */
object AppTimerManager {
    private const val ONE_MINUTE_MS = 60_000L
    private const val TIMER_TICK_MS = 1_000L

    @Volatile
    private var isInitialized = false

    private lateinit var appPreferences: AppPreferences
    private val sessions = mutableMapOf<String, AppPreferences.SessionEntry>()
    private val handler = Handler(Looper.getMainLooper())

    private var expirationListener: ((String) -> Unit)? = null

    private val timerTicker = object : Runnable {
        override fun run() {
            // Vérifie les expirations chaque seconde pour bloquer immédiatement l'app en cours.
            val expiredPackages = synchronized(this@AppTimerManager) {
                removeExpiredSessionsLocked()
            }

            expiredPackages.forEach { packageName ->
                expirationListener?.invoke(packageName)
            }

            synchronized(this@AppTimerManager) {
                if (hasTimedSessionLocked()) {
                    handler.postDelayed(this, TIMER_TICK_MS)
                }
            }
        }
    }

    @Synchronized
    fun initialize(context: Context) {
        if (isInitialized) return

        appPreferences = AppPreferences(context.applicationContext)
        sessions.clear()
        sessions.putAll(appPreferences.getSessions())
        isInitialized = true

        removeExpiredSessionsLocked()
        scheduleTickerLocked()
    }

    @Synchronized
    fun setExpirationListener(listener: ((String) -> Unit)?) {
        ensureInitialized()
        expirationListener = listener
    }

    @Synchronized
    fun isReady(): Boolean {
        return isInitialized
    }

    @Synchronized
    fun startTimedSession(packageName: String, durationMinutes: Int) {
        ensureInitialized()
        val sanitizedMinutes = durationMinutes.coerceAtLeast(1)
        val now = System.currentTimeMillis()

        sessions[packageName] = AppPreferences.SessionEntry(
            packageName = packageName,
            startedAtMillis = now,
            expiresAtMillis = now + sanitizedMinutes * ONE_MINUTE_MS,
            durationMinutes = sanitizedMinutes,
            isUnlimited = false
        )

        persistSessionsLocked()
        scheduleTickerLocked()
    }

    @Synchronized
    fun startUnlimitedSession(packageName: String) {
        ensureInitialized()
        val now = System.currentTimeMillis()
        sessions[packageName] = AppPreferences.SessionEntry(
            packageName = packageName,
            startedAtMillis = now,
            expiresAtMillis = null,
            durationMinutes = null,
            isUnlimited = true
        )

        persistSessionsLocked()
        scheduleTickerLocked()
    }

    @Synchronized
    fun extendSession(packageName: String, extraMinutes: Int) {
        ensureInitialized()
        val minutes = extraMinutes.coerceAtLeast(1)
        val now = System.currentTimeMillis()
        val currentExpiry = sessions[packageName]?.expiresAtMillis
        val baseTime = currentExpiry?.takeIf { it > now } ?: now
        val newExpiry = baseTime + minutes * ONE_MINUTE_MS
        val totalDurationFromNow = ((newExpiry - now) / ONE_MINUTE_MS).toInt().coerceAtLeast(minutes)

        sessions[packageName] = AppPreferences.SessionEntry(
            packageName = packageName,
            startedAtMillis = now,
            expiresAtMillis = newExpiry,
            durationMinutes = totalDurationFromNow,
            isUnlimited = false
        )

        persistSessionsLocked()
        scheduleTickerLocked()
    }

    @Synchronized
    fun hasActiveSession(packageName: String): Boolean {
        ensureInitialized()
        removeExpiredSessionsLocked()
        return sessions.containsKey(packageName)
    }

    @Synchronized
    fun getSession(packageName: String): AppPreferences.SessionEntry? {
        ensureInitialized()
        // Ne supprime pas ici: le service doit pouvoir détecter une session expirée
        // et appliquer le blocage immédiatement sur l'app en premier plan.
        return sessions[packageName]
    }

    @Synchronized
    fun clearSession(packageName: String) {
        ensureInitialized()
        sessions.remove(packageName)
        persistSessionsLocked()
        scheduleTickerLocked()
    }

    @Synchronized
    fun clearAllSessions() {
        ensureInitialized()
        sessions.clear()
        persistSessionsLocked()
        scheduleTickerLocked()
    }

    @Synchronized
    fun onForegroundAppChanged(previousPackageName: String?, currentPackageName: String?) {
        ensureInitialized()
        var modified = false

        if (!previousPackageName.isNullOrBlank() && previousPackageName != currentPackageName) {
            val previousSession = sessions[previousPackageName]
            if (previousSession?.isUnlimited == true) {
                sessions.remove(previousPackageName)
                modified = true
            }
        }

        val expired = removeExpiredSessionsLocked()
        if (modified) {
            persistSessionsLocked()
        }
        if (expired.isNotEmpty()) {
            expired.forEach { packageName ->
                expirationListener?.invoke(packageName)
            }
        }
        scheduleTickerLocked()
    }

    @Synchronized
    fun getActiveSessions(): List<AppPreferences.SessionEntry> {
        ensureInitialized()
        removeExpiredSessionsLocked()
        return sessions.values.sortedBy { it.packageName }
    }

    @Synchronized
    fun getRemainingMillis(packageName: String): Long? {
        ensureInitialized()
        val session = sessions[packageName] ?: return null
        if (session.isUnlimited) return null
        val expiresAt = session.expiresAtMillis ?: return null
        return (expiresAt - System.currentTimeMillis()).coerceAtLeast(0L)
    }

    private fun ensureInitialized() {
        check(isInitialized) {
            "AppTimerManager.initialize(context) doit être appelé avant utilisation."
        }
    }

    @Synchronized
    private fun removeExpiredSessionsLocked(): List<String> {
        val now = System.currentTimeMillis()
        val expiredPackages = sessions.values
            .filter { !it.isUnlimited && (it.expiresAtMillis ?: 0L) <= now }
            .map { it.packageName }

        if (expiredPackages.isNotEmpty()) {
            expiredPackages.forEach { packageName -> sessions.remove(packageName) }
            persistSessionsLocked()
        }
        return expiredPackages
    }

    @Synchronized
    private fun hasTimedSessionLocked(): Boolean {
        return sessions.values.any { !it.isUnlimited }
    }

    @Synchronized
    private fun scheduleTickerLocked() {
        handler.removeCallbacks(timerTicker)
        if (hasTimedSessionLocked()) {
            handler.postDelayed(timerTicker, TIMER_TICK_MS)
        }
    }

    @Synchronized
    private fun persistSessionsLocked() {
        appPreferences.saveSessions(sessions)
    }
}
