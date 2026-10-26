package com.droiddeck.launcher

import android.app.Activity
import android.os.Handler
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.droiddeck.launcher.session.SessionState
import com.droiddeck.launcher.ui.UpdatesActions
import com.droiddeck.launcher.ui.UpdatesState
import com.droiddeck.launcher.update.AppUpdates

internal class UpdatesMenu(private val activity: Activity, private val ui: Handler) {
    private var catalog by mutableStateOf<AppUpdates.Catalog?>(null)
    private var checking by mutableStateOf(false)
    private var error by mutableStateOf<String?>(null)
    private var lastCheck = 0L

    fun state() = UpdatesState(catalog, checking, error)
    fun actions() = UpdatesActions(onCheck = { check(force = true) })
    fun start() { catalog = AppUpdates.cached(activity) }
    fun onResume() { if (!SessionState.running) check(force = false) }

    private fun check(force: Boolean) {
        if (checking) return
        val now = System.currentTimeMillis()
        if (!force && now - lastCheck < 60_000) return
        lastCheck = now
        checking = true
        Thread({
            val result = runCatching { AppUpdates.refresh(activity) }
            ui.post {
                if (activity.isFinishing || activity.isDestroyed) return@post
                checking = false
                result.onSuccess { catalog = it; error = null }
                    .onFailure { if (force || catalog == null) error = activity.getString(R.string.upd_couldnt_check, it.message ?: it.javaClass.simpleName) }
            }
        }, "app-changelog").start()
    }
}
