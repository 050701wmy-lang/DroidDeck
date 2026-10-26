package com.droiddeck.launcher.input

import android.view.KeyEvent

class BackQamInput(private val setPressed: (Boolean) -> Unit) {
    private val held = mutableSetOf<Int>()
    private val cancelled = mutableSetOf<Int>()

    fun onKeyEvent(event: KeyEvent, enabled: Boolean): Boolean {
        if (!enabled) cancel()
        if (event.keyCode != KeyEvent.KEYCODE_BACK) return false
        val device = event.deviceId
        return when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (event.repeatCount > 0) return enabled || device in held || device in cancelled
                cancelled.remove(device)
                if (!enabled || event.isCanceled) return false
                if (held.add(device) && held.size == 1) setPressed(true)
                true
            }
            KeyEvent.ACTION_UP -> {
                val released = held.remove(device)
                val wasCancelled = cancelled.remove(device)
                if (released && held.isEmpty()) setPressed(false)
                released || wasCancelled || enabled
            }
            else -> device in held || device in cancelled
        }
    }

    fun cancel() {
        if (held.isEmpty()) return
        cancelled.addAll(held)
        held.clear()
        setPressed(false)
    }

    fun onDeviceRemoved(device: Int) {
        cancelled.remove(device)
        if (held.remove(device) && held.isEmpty()) setPressed(false)
    }
}
