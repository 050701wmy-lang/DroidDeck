package com.steamdeck.launcher.wayland

import android.view.Choreographer
import android.view.Surface

/**
 * The compositor is a process-wide thing, not an activity's: it is started once, keeps running
 * while the session does, and merely swaps the Surface it presents into as the activity comes and
 * goes. An activity that started it again on re-creation would be starting a second compositor on
 * a socket the first one owns.
 */
object CompositorHost {
    @Volatile
    private var started = false
    private var vsyncRunning = false

    private val vsync = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            WaylandCompositor.nativeVsync(frameTimeNanos)
            if (vsyncRunning) Choreographer.getInstance().postFrameCallback(this)
        }
    }

    /** True when this call was the one that started the compositor. */
    @Synchronized
    fun startOrAttach(
        surface: Surface,
        xdgRuntimeDir: String,
        driverPath: String?,
        libraryName: String?,
        nativeLibDir: String,
        outputWidth: Int,
        outputHeight: Int,
        refreshHz: Float,
    ): Boolean {
        if (started) {
            WaylandCompositor.nativeSetSurface(surface)
            resumeVsync()
            return false
        }
        WaylandCompositor.nativeSetOutputSize(outputWidth, outputHeight)
        WaylandCompositor.nativeSetOutputRefreshRate(refreshHz)
        WaylandCompositor.nativeStartWithSurface(
            surface, xdgRuntimeDir, driverPath, libraryName, nativeLibDir,
        )
        started = true
        resumeVsync()
        return true
    }

    /**
     * The Surface changed size under the compositor (a foldable opening or closing). Android does
     * not always recreate the Surface for that, and a swapchain built for the old size keeps
     * presenting: the system then scales those buffers onto the new window, which on a Fold showed
     * a 16:9 picture stretched to the square panel. Rebinding the window rebuilds the swapchain at
     * the new size, and the scale mode letterboxes as it should.
     */
    @Synchronized
    fun resize(surface: Surface) {
        if (!started) return
        WaylandCompositor.nativeSetSurface(null)
        WaylandCompositor.nativeSetSurface(surface)
    }

    /**
     * The activity is going away. The compositor keeps running with nothing to present into —
     * the guest carries on, and its next frames land on the Surface the next activity brings.
     */
    @Synchronized
    fun detach() {
        if (!started) return
        pauseVsync()
        WaylandCompositor.nativeSetSurface(null)
    }

    /** Frames are only worth drawing while a Surface is attached. */
    private fun resumeVsync() {
        if (vsyncRunning) return
        vsyncRunning = true
        Choreographer.getInstance().postFrameCallback(vsync)
    }

    private fun pauseVsync() {
        vsyncRunning = false
    }

    /**
     * A new session behind a compositor that has already presented: the first-frame notice is
     * one-shot in native code, so it is re-armed here or the loading panel would never leave.
     */
    fun newSession() {
        if (started) WaylandCompositor.nativeResetFirstFrame()
    }

    val isStarted: Boolean get() = started
}
