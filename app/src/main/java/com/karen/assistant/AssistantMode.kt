package com.karen.assistant

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

object AssistantMode {
    fun iron(context: Context) = context.getSharedPreferences("assistant_mode", 0).getBoolean("iron", false)
    fun name(context: Context) = if (iron(context)) "JARVIS" else "KAREN"
    fun icon(context: Context) = if (iron(context)) R.drawable.avatar_core else R.drawable.avatar_spider
    fun textColor(context: Context) = if (iron(context)) 0xFFE0F8FF.toInt() else 0xFF501522.toInt()
    fun hintColor(context: Context) = if (iron(context)) 0xFF87B4C4.toInt() else 0xFF996D75.toInt()
    fun select(context: Context, iron: Boolean) {
        check(context.getSharedPreferences("assistant_mode", 0).edit().putBoolean("iron", iron).commit())
        syncLauncher(context)
    }
    fun syncLauncher(context: Context) {
        val manager = context.packageManager
        val iron = iron(context)
        // Enable the destination before disabling the old alias; keep the running app alive.
        val enabled = if (iron) "IronLauncher" else "SpiderLauncher"
        val disabled = if (iron) "SpiderLauncher" else "IronLauncher"
        manager.setComponentEnabledSetting(ComponentName(context, "${context.packageName}.$enabled"), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        manager.setComponentEnabledSetting(ComponentName(context, "${context.packageName}.$disabled"), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
    }
}
