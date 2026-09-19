package com.the412banner.steamdeck

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Choreographer
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.FrameLayout
import com.the412banner.steamdeck.audio.PulseAudioComponent
import com.the412banner.steamdeck.core.EnvVars
import com.the412banner.steamdeck.core.EnvironmentComponent
import com.the412banner.steamdeck.core.FileUtils
import com.the412banner.steamdeck.core.ProcessHelper
import com.the412banner.steamdeck.gpu.TurnipDriver
import com.the412banner.steamdeck.input.FakeInputWriter
import com.the412banner.steamdeck.input.PadBridge
import com.the412banner.steamdeck.runtime.LinuxNetworkLinkComponent
import com.the412banner.steamdeck.runtime.LinuxRuntime
import com.the412banner.steamdeck.wayland.WaylandCompositor
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The session: our Wayland compositor on this activity's Surface, and gamescope plus Valve's
 * native arm64 Steam client running under proot inside the Linux runtime, presenting into it.
 *
 * Nothing here is Wine. proot and the session script are the whole of the guest side, and the
 * activity's own job is three things: keep a Surface under the compositor, feed it input, and
 * hold the session's host-side components (audio, the network link, the proot process) for as
 * long as the client is up.
 *
 * Ported from Bannerlator's XServerDisplayActivity.setupLinuxSession, which in turn follows
 * WinNative's gamescope runtime (GPL-3.0).
 */
class SessionActivity : Activity(), SurfaceHolder.Callback {
    private lateinit var surfaceView: SurfaceView
    private lateinit var statusView: TextView
    private val components = ArrayList<EnvironmentComponent>()
    private var padBridge: PadBridge? = null
    private var compositorStarted = false
    private var sessionStarted = false
    private var finishing = false
    private var sessionPid = -1
    private var vsyncRunning = false
    private lateinit var sessionLog: File

    private val vsyncCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            WaylandCompositor.nativeVsync(frameTimeNanos)
            if (vsyncRunning) Choreographer.getInstance().postFrameCallback(this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        goFullscreen()

        if (!LinuxRuntime.isInstalled(this)) {
            Log.e(TAG, "the Linux runtime is not installed")
            finish()
            return
        }

        val root = FrameLayout(this)
        surfaceView = SurfaceView(this)
        surfaceView.holder.addCallback(this)
        root.addView(surfaceView)
        // Until the client's first frame arrives there is nothing on screen for a minute or more
        // on a first run, so the session's own milestones are shown instead of a black panel.
        statusView = TextView(this).apply {
            setPadding(48, 48, 48, 48)
            setTextColor(0xFFDDDDDD.toInt())
            textSize = 14f
            text = getString(R.string.session_starting)
        }
        root.addView(statusView)
        setContentView(root)

        WaylandCompositor.setFirstFrameListener {
            runOnUiThread { statusView.visibility = View.GONE }
        }
    }

    // ── Compositor ──────────────────────────────────────────────────────────────────────────

    override fun surfaceCreated(holder: SurfaceHolder) {
        if (compositorStarted) {
            WaylandCompositor.nativeSetSurface(holder.surface)
            return
        }
        compositorStarted = true

        val runtimeDir = File(filesDir, ".wayland-rt").apply { mkdirs() }
        // The compositor hands this keymap to wl_keyboard clients, which is how the guest reads
        // the evdev codes we inject.
        FileUtils.copyAsset(this, "wayland/keymap.xkb", File(runtimeDir, "keymap.xkb"))

        // Turnip, not the system Adreno driver: importing the dma-bufs gamescope commits needs
        // VK_EXT_image_drm_format_modifier, which the system driver does not implement.
        val turnip = TurnipDriver(this)
        val driverId = turnip.install()
        val driverPath = driverId?.let { turnip.driverPath(it) }
        val libraryName = driverId?.let { turnip.libraryName(it) }

        val size = outputSize()
        WaylandCompositor.nativeSetOutputSize(size.first, size.second)
        WaylandCompositor.nativeSetOutputRefreshRate(refreshHz())
        WaylandCompositor.nativeStartWithSurface(
            holder.surface, runtimeDir.path, driverPath, libraryName,
            applicationInfo.nativeLibraryDir,
        )
        vsyncRunning = true
        Choreographer.getInstance().postFrameCallback(vsyncCallback)

        Thread({ startSession(runtimeDir, size) }, "session-start").start()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        WaylandCompositor.nativeSetSurface(null)
    }

    /**
     * The size the session renders at. gamescope is told this and scales its output onto whatever
     * the panel is, so a 1440p phone can run the client at 1080p without the client knowing.
     */
    private fun outputSize(): Pair<Int, Int> {
        val metrics = resources.displayMetrics
        var width = maxOf(metrics.widthPixels, metrics.heightPixels)
        var height = minOf(metrics.widthPixels, metrics.heightPixels)
        // The client's CEF is the heaviest thing in the session; above 1080p it costs frames for
        // nothing anyone can see on a phone panel.
        if (height > 1080) {
            width = width * 1080 / height
            height = 1080
        }
        // Odd sizes upset the scaler; both dimensions even is what every mode here would be.
        return Pair(width and 1.inv(), height and 1.inv())
    }

    private fun refreshHz(): Float {
        val display = if (Build.VERSION.SDK_INT >= 30) display else windowManager.defaultDisplay
        val hz = display?.refreshRate ?: 60f
        return if (hz > 1f) hz else 60f
    }

    // ── Session ─────────────────────────────────────────────────────────────────────────────

    private fun startSession(runtimeDir: File, size: Pair<Int, Int>) {
        try {
            LinuxRuntime.writeAccounts(this)
        } catch (e: Exception) {
            Log.e(TAG, "could not write the guest's passwd/group", e)
            endSession(-1)
            return
        }

        val root = LinuxRuntime.rootDir(this)
        val sessionRoot = LinuxRuntime.sessionRoot(this).apply { mkdirs() }
        stageSessionFiles(root)

        val logDir = LinuxRuntime.debugLogDir().apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        sessionLog = File(logDir, "session-$stamp.log")

        val guest = ArrayList<String>()
        guest.add("/usr/bin/env")
        guest.add("-i")
        guest.add("HOME=/root")
        guest.add("USER=root")
        guest.add("PATH=/usr/local/bin:/usr/bin:/bin")
        guest.add("TERM=xterm-256color")
        guest.add("LANG=C.UTF-8")
        guest.add("XDG_RUNTIME_DIR=" + runtimeDir.path)
        guest.add("XDG_SESSION_TYPE=wayland")
        guest.add("WAYLAND_DISPLAY=wayland-0")
        guest.add("GAMESCOPE_FORCE_GENERAL_QUEUE=1")
        // Steam's CEF needs GL and the rootfs ships no native GL driver: route it through Zink.
        guest.add("MESA_LOADER_DRIVER_OVERRIDE=zink")
        guest.add("GALLIUM_DRIVER=zink")
        guest.add("LIBGL_KOPPER_DRI2=true")
        LinuxRuntime.vulkanIcd(this)?.let { guest.add("VK_ICD_FILENAMES=" + it.path) }

        val pulse = PulseAudioComponent(this)
        pulse.setContext(this)
        guest.add("PULSE_SERVER=unix:" + pulse.socket().absolutePath)
        components.add(pulse)

        guest.add("BL_WIDTH=" + size.first)
        guest.add("BL_HEIGHT=" + size.second)
        guest.add("BL_FPS=0")
        // What gamescope advertises as the refresh rate, and what a game reads as the display's.
        // Left unset it says 60, and a 120 Hz panel then offers only 60 Hz in game settings.
        guest.add("BL_REFRESH=" + Math.round(refreshHz()))
        guest.add("BL_LOG=" + sessionLog.path)
        guest.add("BL_DEBUG_DIR=" + File(logDir, "session-$stamp").path)

        // Controllers. WinHandler is a Wine thing and there is none here, so the pad is published
        // into the rings by PadBridge instead; the reader is the same interposer either way.
        val fakeInputDir = File(sessionRoot, "dev/input").apply { mkdirs() }
        val controllersOn = !File(Environment.getExternalStorageDirectory(), NO_PAD_SWITCH).exists()
        if (controllersOn) {
            FakeInputWriter.prepareRingSlots(fakeInputDir, 4)
            guest.add("FAKE_EVDEV_DIR=" + fakeInputDir.path)
            val rings = FakeInputWriter.getRingEnv(fakeInputDir)
            if (!rings.isNullOrEmpty()) guest.add("FAKE_EVDEV_MEMFD_PATHS=$rings")
            // SDL and Steam key their mapping database on bus+vendor+product: only a known
            // identity gets the standard layout without the user configuring the pad by hand.
            guest.add("FAKE_EVDEV_IDENTITY=xbox360")
            guest.add("FAKE_EVDEV_VIBRATION=1")
            guest.add("FAKE_EVDEV_STEAM_VIRTUAL=1")
            // No udev runs in the runtime: SDL and hidapi scan /dev/input themselves, and the
            // netlink monitor they open anyway is answered by the session shim's stand-in.
            guest.add("SDL_JOYSTICK_DISABLE_UDEV=1")
            guest.add("SDL_HIDAPI_JOYSTICK_DISABLE_UDEV=1")
            guest.add("SDL_JOYSTICK_HIDAPI=0")
            if (File(Environment.getExternalStorageDirectory(), PAD_LOG_SWITCH).exists()) {
                guest.add("FAKE_EVDEV_LOG=1")
            }
            padBridge = PadBridge(fakeInputDir).also { it.start() }
        }
        guest.add(LinuxRuntime.SESSION_SCRIPT)
        guest.add(LinuxRuntime.MODE_STEAM)

        // Android has no /dev/shm; the cache stands in for it and, unlike the real thing, keeps
        // whatever a session leaves behind. The client abandons tens of megabytes of streams a run.
        FileUtils.clear(File(cacheDir, "shm"))

        val binds = ArrayList<String>()
        // Apps may not list /dev/input, so the fake nodes are bound in as the whole directory.
        if (controllersOn) binds.add(fakeInputDir.path + ":/dev/input")

        val command = LinuxRuntime.command(
            this, sessionRoot, runtimeDir, Environment.getExternalStorageDirectory(), binds, guest,
        )

        val hostEnv = EnvVars()
        hostEnv.put("PROOT_LOADER", LinuxRuntime.prootLoader(this).path)
        hostEnv.put("PROOT_TMP_DIR", cacheDir.path)
        // proot links against a libtalloc beside it, and Android's linker does not search an
        // executable's own directory: unnamed, the process dies before it starts and says so only
        // in `logcat -b crash`.
        val prootLibs = LinuxRuntime.prootLibraryPath(this)
        if (prootLibs.isNotEmpty()) hostEnv.put("LD_LIBRARY_PATH", prootLibs)

        val networkLink = LinuxNetworkLinkComponent(this, LinuxRuntime.rootDir(this))
        networkLink.setContext(this)
        networkLink.publish()
        components.add(networkLink)
        components.forEach { it.start() }

        watchSessionLog()
        val line = command.joinToString(" ") { it.replace(" ", "\\ ") }
        sessionPid = ProcessHelper.exec(line, hostEnv.toStringArray(), root, { status ->
            Log.i(TAG, "session ended: $status")
            endSession(status)
        }, null)
        sessionStarted = true
        Log.i(TAG, "session pid $sessionPid, log ${sessionLog.path}")
    }

    /**
     * The libraries and scripts the session runs, refreshed from the apk at every launch.
     *
     * The runtime image carries its own copies, but a runtime installed months ago carries the
     * copies of that day and a device has no way to replace them from outside the app. Staging
     * them here is how a fix inside the session shim, the controller reader or one of the scripts
     * reaches an already-installed runtime without a ~790 MB re-download. Each lands through a
     * rename, so a session that still has one mapped keeps the file it opened.
     */
    private fun stageSessionFiles(root: File) {
        val files = arrayOf(
            "libblsession.so" to "usr/local/lib/libblsession.so",
            "libfakeinput.so" to "usr/local/lib/libfakeinput.so",
            "usr/local/bin/bannerlator-session" to "usr/local/bin/bannerlator-session",
            "usr/local/bin/bannerlator-steam-compat" to "usr/local/bin/bannerlator-steam-compat",
            "usr/local/bin/bannerlator-steam-install" to "usr/local/bin/bannerlator-steam-install",
            "usr/local/bin/bannerlator-steam-library" to "usr/local/bin/bannerlator-steam-library",
            "usr/local/bin/bannerlator-seed-redists" to "usr/local/bin/bannerlator-seed-redists",
        )
        for ((asset, relative) in files) {
            val target = File(root, relative)
            val staged = File(target.parentFile, target.name + ".staged")
            var installed = false
            try {
                target.parentFile?.mkdirs()
                assets.open("linuxfs/$asset").use { input ->
                    staged.outputStream().use { output -> FileUtils.copy(input, output) }
                }
                installed = staged.setExecutable(true, false) && staged.renameTo(target)
            } catch (e: Exception) {
                Log.w(TAG, "could not stage $relative", e)
            } finally {
                if (!installed) staged.delete()
            }
            if (!installed) Log.e(TAG, "$relative NOT staged")
        }
        // What every process in the session preloads. LD_PRELOAD in the environment would not
        // survive: the Steam client rebuilds it for each process it starts and appends its own
        // overlay entry without a separator, which silently drops whatever was there.
        val preload = StringBuilder("/usr/local/lib/libblsession.so\n")
        if (!File(Environment.getExternalStorageDirectory(), NO_PAD_SWITCH).exists()) {
            preload.append("/usr/local/lib/libfakeinput.so\n")
        }
        val etc = File(root, "etc").apply { mkdirs() }
        val staged = File(etc, "ld.so.preload.staged")
        if (!FileUtils.writeString(staged, preload.toString()) || !staged.renameTo(File(etc, "ld.so.preload"))) {
            staged.delete()
            Log.e(TAG, "could not write ld.so.preload")
        }
    }

    /** Mirrors the session script's "== STEP" milestones onto the screen while it starts. */
    private fun watchSessionLog() {
        val handler = Handler(Looper.getMainLooper())
        Thread({
            var shown = ""
            while (!finishing) {
                try {
                    Thread.sleep(500)
                    val text = FileUtils.readString(sessionLog) ?: continue
                    val step = text.lineSequence().lastOrNull { it.startsWith("== STEP") } ?: continue
                    val message = step.substringAfter("== STEP ").substringAfter(' ')
                    if (message != shown) {
                        shown = message
                        handler.post { if (statusView.visibility == View.VISIBLE) statusView.text = message }
                    }
                } catch (e: InterruptedException) {
                    return@Thread
                } catch (ignored: Exception) {
                }
            }
        }, "session-log").start()
    }

    private fun endSession(status: Int) {
        if (finishing) return
        finishing = true
        runOnUiThread {
            if (status != 0) {
                statusView.visibility = View.VISIBLE
                statusView.text = getString(R.string.session_ended, status, sessionLogName())
            }
            // A moment on screen, so a failure is readable rather than a flash of black.
            Handler(Looper.getMainLooper()).postDelayed({ finish() }, if (status == 0) 0 else 4000)
        }
    }

    private fun sessionLogName(): String =
        if (::sessionLog.isInitialized) "${LinuxRuntime.DEBUG_LOG_DIR}/${sessionLog.name}" else "-"

    // ── Input ───────────────────────────────────────────────────────────────────────────────

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (padBridge?.onKeyEvent(event) == true) return true
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (padBridge?.onMotionEvent(event) == true) return true
        return super.dispatchGenericMotionEvent(event)
    }

    /**
     * Touch as a mouse: where you touch is where the pointer goes, and a tap is a left click.
     * The compositor's pointer space is a fixed 1920x1080 whatever the output size, so the view's
     * coordinates are scaled into it.
     */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val width = surfaceView.width.takeIf { it > 0 } ?: return false
        val height = surfaceView.height.takeIf { it > 0 } ?: return false
        val action = when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> 0
            MotionEvent.ACTION_MOVE -> 1
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> 2
            else -> return false
        }
        val x = (event.x / width * 1920f).toInt().coerceIn(0, 1919)
        val y = (event.y / height * 1080f).toInt().coerceIn(0, 1079)
        WaylandCompositor.nativeSendPointer(action, x, y)
        return true
    }

    // ── Lifecycle ───────────────────────────────────────────────────────────────────────────

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) goFullscreen()
    }

    private fun goFullscreen() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    override fun onDestroy() {
        finishing = true
        vsyncRunning = false
        padBridge?.stop()
        // proot runs with --kill-on-exit, so killing it takes the whole guest tree with it.
        if (sessionPid != -1) {
            android.os.Process.killProcess(sessionPid)
            sessionPid = -1
        }
        components.reversed().forEach {
            try {
                it.stop()
            } catch (e: Exception) {
                Log.w(TAG, "stopping ${it.javaClass.simpleName}", e)
            }
        }
        components.clear()
        FakeInputWriter.releaseAllRingSlots()
        WaylandCompositor.setFirstFrameListener(null)
        super.onDestroy()
    }

    companion object {
        private const val TAG = "SessionActivity"
        /** Turns the whole controller feature off, for a true baseline on a device we cannot reach. */
        private const val NO_PAD_SWITCH = "Download/steamdeck-no-pad"
        /** Traces every interposer call into the session log. */
        private const val PAD_LOG_SWITCH = "Download/steamdeck-pad-log"
    }
}
