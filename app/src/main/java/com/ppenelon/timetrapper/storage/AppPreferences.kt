package com.ppenelon.timetrapper.storage

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * Stockage local minimal:
 * - apps surveillées
 * - sessions actives
 * - option de logs debug
 */
class AppPreferences(context: Context) {

    data class MonitoredApp(
        val title: String,
        val packageName: String
    )

    data class SessionEntry(
        val packageName: String,
        val startedAtMillis: Long,
        val expiresAtMillis: Long?,
        val durationMinutes: Int?,
        val isUnlimited: Boolean
    )

    companion object {
        private const val PREFS_NAME = "time_trapper_prefs"
        private const val KEY_MONITORED_APPS_JSON = "monitored_apps_json"
        private const val KEY_MONITORED_APPS_INITIALIZED = "monitored_apps_initialized"

        // Clés legacy (v1) conservées pour migration.
        private const val KEY_MONITORED_PACKAGES = "monitored_packages"
        private const val KEY_MONITORED_PACKAGES_INITIALIZED = "monitored_packages_initialized"

        private const val KEY_DEBUG_LOGS = "debug_logs_enabled"
        private const val KEY_SESSIONS_JSON = "sessions_json"
    }

    private val sharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val defaultMonitoredApps = listOf(
        MonitoredApp(title = "YouTube", packageName = "com.google.android.youtube"),
        MonitoredApp(title = "Instagram", packageName = "com.instagram.android")
    )

    fun getMonitoredApps(): List<MonitoredApp> {
        ensureDefaultMonitoredAppsInitialized()
        val rawJson = sharedPreferences.getString(KEY_MONITORED_APPS_JSON, "[]") ?: "[]"
        return parseMonitoredApps(rawJson).sortedBy { it.title.lowercase(Locale.US) }
    }

    fun findMonitoredApp(packageName: String): MonitoredApp? {
        val normalizedPackage = packageName.trim().lowercase(Locale.US)
        if (normalizedPackage.isBlank()) return null
        return getMonitoredApps().firstOrNull { it.packageName == normalizedPackage }
    }

    fun getMonitoredPackages(): Set<String> {
        return getMonitoredApps().map { it.packageName }.toSet()
    }

    fun upsertMonitoredApp(
        title: String,
        packageName: String,
        previousPackageName: String? = null
    ) {
        ensureDefaultMonitoredAppsInitialized()

        val normalizedTitle = title.trim()
        val normalizedPackage = packageName.trim().lowercase(Locale.US)
        if (normalizedTitle.isBlank() || normalizedPackage.isBlank()) return

        val apps = getMonitoredApps().toMutableList()
        val normalizedPreviousPackage = previousPackageName?.trim()?.lowercase(Locale.US)

        if (!normalizedPreviousPackage.isNullOrBlank() && normalizedPreviousPackage != normalizedPackage) {
            apps.removeAll { it.packageName == normalizedPreviousPackage }
        }

        val replacement = MonitoredApp(
            title = normalizedTitle,
            packageName = normalizedPackage
        )
        val existingIndex = apps.indexOfFirst { it.packageName == normalizedPackage }
        if (existingIndex >= 0) {
            apps[existingIndex] = replacement
        } else {
            apps.add(replacement)
        }

        saveMonitoredApps(apps)
    }

    fun removeMonitoredApp(packageName: String) {
        val normalizedPackage = packageName.trim().lowercase(Locale.US)
        if (normalizedPackage.isBlank()) return

        val updatedApps = getMonitoredApps().toMutableList().apply {
            removeAll { it.packageName == normalizedPackage }
        }
        saveMonitoredApps(updatedApps)
    }

    fun addMonitoredPackage(packageName: String) {
        val normalizedPackageName = packageName.trim().lowercase(Locale.US)
        if (normalizedPackageName.isBlank()) return
        upsertMonitoredApp(
            title = inferTitleFromPackageName(normalizedPackageName),
            packageName = normalizedPackageName
        )
    }

    fun removeMonitoredPackage(packageName: String) {
        removeMonitoredApp(packageName)
    }

    fun setMonitoredPackages(packages: Set<String>) {
        val mappedApps = packages
            .map { it.trim().lowercase(Locale.US) }
            .filter { it.isNotBlank() }
            .distinct()
            .map { packageName ->
                MonitoredApp(
                    title = inferTitleFromPackageName(packageName),
                    packageName = packageName
                )
            }
        saveMonitoredApps(mappedApps)
    }

    fun isDebugLogsEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_DEBUG_LOGS, false)
    }

    fun setDebugLogsEnabled(isEnabled: Boolean) {
        sharedPreferences.edit()
            .putBoolean(KEY_DEBUG_LOGS, isEnabled)
            .apply()
    }

    fun getSessions(): MutableMap<String, SessionEntry> {
        val rawJson = sharedPreferences.getString(KEY_SESSIONS_JSON, "[]") ?: "[]"
        val sessions = mutableMapOf<String, SessionEntry>()

        try {
            val jsonArray = JSONArray(rawJson)
            for (index in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(index)
                val packageName = jsonObject.optString("packageName").orEmpty()
                if (packageName.isBlank()) continue

                val startedAt = jsonObject.optLong("startedAtMillis", 0L)
                val isUnlimited = jsonObject.optBoolean("isUnlimited", false)
                val expiresAt = if (jsonObject.isNull("expiresAtMillis")) {
                    null
                } else {
                    jsonObject.optLong("expiresAtMillis")
                }
                val durationMinutes = if (jsonObject.isNull("durationMinutes")) {
                    null
                } else {
                    jsonObject.optInt("durationMinutes")
                }

                sessions[packageName] = SessionEntry(
                    packageName = packageName,
                    startedAtMillis = startedAt,
                    expiresAtMillis = expiresAt,
                    durationMinutes = durationMinutes,
                    isUnlimited = isUnlimited
                )
            }
        } catch (_: Exception) {
            return mutableMapOf()
        }

        return sessions
    }

    fun saveSessions(sessions: Map<String, SessionEntry>) {
        val jsonArray = JSONArray()
        sessions.values.sortedBy { it.packageName }.forEach { session ->
            jsonArray.put(
                JSONObject().apply {
                    put("packageName", session.packageName)
                    put("startedAtMillis", session.startedAtMillis)
                    put("isUnlimited", session.isUnlimited)
                    put("expiresAtMillis", session.expiresAtMillis ?: JSONObject.NULL)
                    put("durationMinutes", session.durationMinutes ?: JSONObject.NULL)
                }
            )
        }

        sharedPreferences.edit()
            .putString(KEY_SESSIONS_JSON, jsonArray.toString())
            .apply()
    }

    private fun ensureDefaultMonitoredAppsInitialized() {
        if (sharedPreferences.getBoolean(KEY_MONITORED_APPS_INITIALIZED, false)) {
            return
        }

        val legacyPackages = sharedPreferences.getStringSet(KEY_MONITORED_PACKAGES, emptySet())
            ?.map { it.trim().lowercase(Locale.US) }
            ?.filter { it.isNotBlank() }
            ?.toSet()
            ?: emptySet()

        if (legacyPackages.isNotEmpty()) {
            setMonitoredPackages(legacyPackages)
            return
        }

        saveMonitoredApps(defaultMonitoredApps)
    }

    private fun saveMonitoredApps(apps: List<MonitoredApp>) {
        val sanitizedApps = apps
            .map {
                MonitoredApp(
                    title = it.title.trim(),
                    packageName = it.packageName.trim().lowercase(Locale.US)
                )
            }
            .filter { it.title.isNotBlank() && it.packageName.isNotBlank() }
            .distinctBy { it.packageName }
            .sortedBy { it.title.lowercase(Locale.US) }

        val jsonArray = JSONArray()
        sanitizedApps.forEach { app ->
            jsonArray.put(
                JSONObject().apply {
                    put("title", app.title)
                    put("packageName", app.packageName)
                }
            )
        }

        sharedPreferences.edit()
            .putBoolean(KEY_MONITORED_APPS_INITIALIZED, true)
            .putString(KEY_MONITORED_APPS_JSON, jsonArray.toString())
            .putBoolean(KEY_MONITORED_PACKAGES_INITIALIZED, true)
            .putStringSet(KEY_MONITORED_PACKAGES, sanitizedApps.map { it.packageName }.toSet())
            .apply()
    }

    private fun parseMonitoredApps(rawJson: String): List<MonitoredApp> {
        val apps = mutableListOf<MonitoredApp>()
        try {
            val jsonArray = JSONArray(rawJson)
            for (index in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(index)
                val packageName = jsonObject.optString("packageName").trim().lowercase(Locale.US)
                if (packageName.isBlank()) continue

                val titleFromJson = jsonObject.optString("title").trim()
                val title = if (titleFromJson.isBlank()) {
                    inferTitleFromPackageName(packageName)
                } else {
                    titleFromJson
                }

                apps.add(MonitoredApp(title = title, packageName = packageName))
            }
        } catch (_: Exception) {
            return emptyList()
        }
        return apps.distinctBy { it.packageName }
    }

    private fun inferTitleFromPackageName(packageName: String): String {
        val lastSegment = packageName.substringAfterLast('.', packageName)
        if (lastSegment.isBlank()) return packageName

        return lastSegment
            .split('_', '-', '.')
            .filter { it.isNotBlank() }
            .joinToString(" ") { segment ->
                segment.replaceFirstChar { char ->
                    if (char.isLowerCase()) char.titlecase(Locale.US) else char.toString()
                }
            }
            .ifBlank { packageName }
    }
}
