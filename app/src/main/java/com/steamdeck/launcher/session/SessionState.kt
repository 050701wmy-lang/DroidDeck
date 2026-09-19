package com.steamdeck.launcher.session

import java.io.File

/**
 * The little that the activity and the service have to agree on. Process-wide rather than passed
 * through intents, because both halves live in one process and the point of the split is that
 * either can outlive the other.
 */
object SessionState {
    @Volatile
    var running = false

    /** The size gamescope was told to render at; set by the activity before the service starts. */
    @Volatile
    var outputSize: Pair<Int, Int> = Pair(1920, 1080)

    @Volatile
    var refreshHz: Float = 60f

    /** Where this session is writing its log, for the "it ended" message. */
    @Volatile
    var logFile: File? = null

    /** The fake evdev directory, once the service has prepared the rings. */
    @Volatile
    var fakeInputDir: File? = null

    /** Told when the session ends, so a visible activity can close itself. */
    @Volatile
    var endListener: ((Int) -> Unit)? = null

    fun notifyEnded(status: Int) {
        endListener?.invoke(status)
    }
}
