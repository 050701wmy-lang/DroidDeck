package com.steamdeck.launcher.session

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import com.steamdeck.launcher.R
import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets

/**
 * The loading panel's brain: what to say, read out of the session log, and a clock so a user can
 * see that time is passing even when the log is quiet. The runtime script marks its milestones
 * with "== STEP"; the client's own bootstrap does not, but it does print its download progress,
 * and lifting that out is the difference between "starting the Steam client" for three minutes
 * and a percentage that moves.
 */
class LoadingPanel(private val root: View) {
    private val step = root.findViewById<TextView>(R.id.loading_step)
    private val progress = root.findViewById<ProgressBar>(R.id.loading_progress)
    private val elapsed = root.findViewById<TextView>(R.id.loading_elapsed)
    private val hint = root.findViewById<TextView>(R.id.loading_hint)
    private val hints = root.resources.getStringArray(R.array.loading_hints)
    private val handler = Handler(Looper.getMainLooper())
    private val startedAt = SystemClock.elapsedRealtime()
    private var ticking = false

    private val tick = object : Runnable {
        override fun run() {
            if (!ticking) return
            val seconds = (SystemClock.elapsedRealtime() - startedAt) / 1000
            elapsed.text = root.context.getString(R.string.loading_elapsed,
                String.format(java.util.Locale.US, "%d:%02d", seconds / 60, seconds % 60))
            // A new hint every eight seconds; the first one is the one that matters most.
            hint.text = hints[((seconds / 8) % hints.size).toInt()]
            handler.postDelayed(this, 1000)
        }
    }

    val isVisible: Boolean get() = root.visibility == View.VISIBLE

    fun show() {
        root.visibility = View.VISIBLE
        if (!ticking) {
            ticking = true
            handler.post(tick)
        }
    }

    fun hide() {
        ticking = false
        root.visibility = View.GONE
    }

    /** The session ended without a picture: say so where the milestones were. */
    fun showEnded(message: String) {
        show()
        ticking = false
        step.text = message
        progress.visibility = View.INVISIBLE
        elapsed.text = ""
        hint.text = ""
    }

    /** Re-reads the end of the log and updates the line and the bar. Call from the UI thread. */
    fun update(log: File?) {
        val state = read(log) ?: return
        if (step.text != state.message) step.text = state.message
        if (state.percent < 0) {
            if (!progress.isIndeterminate) progress.isIndeterminate = true
        } else {
            if (progress.isIndeterminate) progress.isIndeterminate = false
            progress.progress = state.percent
        }
    }

    private class State(val message: String, val percent: Int)

    /**
     * Only the tail is read: the client alone writes megabytes an hour, and this runs twice a
     * second for as long as the panel is up.
     */
    private fun read(log: File?): State? {
        if (log == null || !log.isFile) return null
        val text = try {
            RandomAccessFile(log, "r").use { file ->
                val length = file.length()
                val want = minOf(length, TAIL_BYTES)
                file.seek(length - want)
                val bytes = ByteArray(want.toInt())
                file.readFully(bytes)
                String(bytes, StandardCharsets.UTF_8)
            }
        } catch (e: Exception) {
            return null
        }
        var stepAt = -1
        var stepText: String? = null
        var downloadAt = -1
        var downloadPercent = -1
        var clientDownloadAt = -1
        var clientDownload: String? = null
        var clientPercent = -1
        var offset = 0
        for (line in text.split('\n')) {
            val at = offset
            offset += line.length + 1
            when {
                line.startsWith("== STEP ") -> {
                    stepAt = at
                    stepText = line.substringAfter("== STEP ").substringAfter(' ')
                    // "downloading Steam: bins_hardware_all (1/17)" — the installer's own count.
                    val m = INSTALL_COUNT.find(line)
                    if (m != null) {
                        clientDownloadAt = at
                        clientDownload = m.groupValues[1]
                        clientPercent = m.groupValues[2].toInt() * 100 / maxOf(1, m.groupValues[3].toInt())
                    }
                }
                else -> {
                    // "Downloading update (350129 of 666167 KB)..." — the client's bootstrap.
                    val m = UPDATE_PROGRESS.find(line)
                    if (m != null) {
                        downloadAt = at
                        val done = m.groupValues[1].toLong()
                        val total = maxOf(1L, m.groupValues[2].toLong())
                        downloadPercent = (done * 100 / total).toInt()
                    }
                }
            }
        }
        val context = root.context
        return when {
            downloadAt > stepAt && downloadPercent >= 0 ->
                State(context.getString(R.string.loading_download, downloadPercent), downloadPercent)
            clientDownloadAt == stepAt && clientDownload != null ->
                State(context.getString(R.string.loading_client_download, clientDownload), clientPercent)
            stepText != null -> State(stepText, -1)
            else -> null
        }
    }

    companion object {
        private const val TAIL_BYTES = 48L * 1024
        private val UPDATE_PROGRESS = Regex("""Downloading update \((\d+) of (\d+) KB\)""")
        private val INSTALL_COUNT = Regex("""downloading Steam: (\S+) \((\d+)/(\d+)\)""")
    }
}
