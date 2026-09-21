package com.steamdeck.launcher.session

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * One folder per session under `Download/SteamDeck/`, holding everything that session recorded:
 *
 * ```
 *   Download/SteamDeck/session-20260921-161256/
 *       device.txt     what this device is, and every setting the session ran with
 *       session.log    the guest session: proot, gamescope, the client's stdout
 *       wayland.log    the app's compositor
 *       steam.log      the Steam client's own log, scrubbed  (Steam mode)
 *       steam/         the rest of the client's logs, scrubbed  (Steam mode)
 *       desktop.log    labwc, the panel and the programs on it  (desktop mode)
 * ```
 *
 * The folder is claimed by whoever starts first - the activity starts the compositor before the
 * service starts the session - so both write into the same one, and a recreated activity (a
 * foldable opening mid-session) joins the folder in progress instead of opening another.
 */
object SessionPaths {
    private const val TAG = "SessionPaths"

    @Volatile
    private var dir: File? = null

    /** The folder for the session now starting, or the one already in progress. */
    @Synchronized
    fun beginOrCurrent(context: Context): File {
        dir?.let { if (it.isDirectory) return it }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val made = File(SessionFiles.logDirectory(context), "session-$stamp")
        if (!made.isDirectory && !made.mkdirs()) Log.e(TAG, "could not create $made")
        dir = made
        Log.i(TAG, "session logs: $made")
        return made
    }

    /** The current session's folder, or null between sessions. */
    fun current(): File? = dir

    /** A file in the current session's folder, or null between sessions. */
    fun file(name: String): File? = dir?.let { File(it, name) }

    /** Let the next session claim a new folder. Called when a session ends. */
    @Synchronized
    fun release() {
        dir = null
    }
}
