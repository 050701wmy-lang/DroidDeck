package com.steamdeck.launcher.session

import android.content.Context

/** The in-session switches: the HUD and how the on-screen controls decide to appear. */
object SessionPrefs {
    const val OSC_AUTO = "auto"
    const val OSC_ALWAYS = "always"
    const val OSC_NEVER = "never"

    private fun prefs(context: Context) = context.getSharedPreferences("session", Context.MODE_PRIVATE)

    fun hudEnabled(context: Context): Boolean = prefs(context).getBoolean("hud", true)

    fun setHudEnabled(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean("hud", on).apply()
    }

    fun oscMode(context: Context): String = prefs(context).getString("osc", OSC_AUTO) ?: OSC_AUTO

    fun setOscMode(context: Context, mode: String) {
        prefs(context).edit().putString("osc", mode).apply()
    }
}
