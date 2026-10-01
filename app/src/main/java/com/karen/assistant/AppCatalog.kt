package com.karen.assistant

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo

data class InstalledApp(val label: String, val packageName: String, val system: Boolean, val launch: Intent?)
class AppCatalog(private val context: Context) {
    fun scan(): List<InstalledApp> = context.packageManager.getInstalledApplications(0).map { app ->
        InstalledApp(context.packageManager.getApplicationLabel(app).toString(), app.packageName, app.flags and ApplicationInfo.FLAG_SYSTEM != 0, context.packageManager.getLaunchIntentForPackage(app.packageName))
    }.sortedBy { it.label.lowercase() }
    fun candidates(query: String): List<Pair<InstalledApp, Double>> = scan()
        .map { it to AppNames.score(query, it.label, it.packageName) }.filter { it.second <= 0.32 }.sortedBy { it.second }
}
