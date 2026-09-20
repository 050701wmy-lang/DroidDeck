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

    const val TOUCH_AUTO = "auto"
    const val TOUCH_PAD = "touchpad"
    const val TOUCH_DIRECT = "direct"

    /** How touch drives the pointer: a touchpad (drag moves it from where it is) or direct
     *  (it jumps under the finger). Auto = touchpad on the desktop, direct in Steam. */
    fun touchMode(context: Context): String = prefs(context).getString("touch", TOUCH_AUTO) ?: TOUCH_AUTO

    fun setTouchMode(context: Context, mode: String) {
        prefs(context).edit().putString("touch", mode).apply()
    }

    const val SHAPE_AUTO = "auto"
    const val SHAPE_WIDE = "16:9"

    /**
     * The shape of the display the session presents: the panel's own (never narrower than 16:9)
     * or a fixed 16:9. A foldable defaults to 16:9, which sits with modest bars on either of its
     * panels; the panel's own shape would fit one and leave a strip on the other, and gamescope's
     * display cannot change size once the session is up.
     */
    fun shapeMode(context: Context): String =
        prefs(context).getString("shape", null)
            ?: if (context.packageManager.hasSystemFeature("android.hardware.sensor.hinge_angle")) SHAPE_WIDE else SHAPE_AUTO

    fun setShapeMode(context: Context, mode: String) {
        prefs(context).edit().putString("shape", mode).apply()
    }

    fun oscMode(context: Context): String = prefs(context).getString("osc", OSC_AUTO) ?: OSC_AUTO

    fun setOscMode(context: Context, mode: String) {
        prefs(context).edit().putString("osc", mode).apply()
    }
}
