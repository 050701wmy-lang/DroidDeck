package com.steamdeck.launcher

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.hardware.input.InputManager
import android.os.Environment
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import com.steamdeck.launcher.core.FileUtils
import com.steamdeck.launcher.gpu.TurnipDriver
import com.steamdeck.launcher.input.EvdevKeys
import com.steamdeck.launcher.input.OnScreenControls
import com.steamdeck.launcher.input.PadBridge
import com.steamdeck.launcher.runtime.LinuxRuntime
import com.steamdeck.launcher.session.SessionService
import com.steamdeck.launcher.session.SessionState
import com.steamdeck.launcher.wayland.CompositorHost
import com.steamdeck.launcher.wayland.WaylandCompositor
import java.io.File

/**
 * The session's screen: our Wayland compositor presenting onto this activity's Surface, and the
 * input that reaches it. The session itself — gamescope, the Steam client, audio — belongs to
 * [SessionService] and keeps running when this activity does not exist, which is what lets the
 * user leave Big Picture for another app and come back to it still signed in and still
 * downloading.
 *
 * So there are only three jobs here: hold a Surface under the compositor, forward input, and show
 * the session's milestones until its first frame arrives.
 */
class SessionActivity : Activity(), SurfaceHolder.Callback {
    private lateinit var surfaceView: SurfaceView
    private lateinit var statusView: TextView
    private var padBridge: PadBridge? = null
    private var onScreenControls: OnScreenControls? = null
    private var watching = true

    /**
     * Shows the on-screen pad when nothing is plugged in and takes it away the moment something
     * is — a user with a controller in their hands should not be looking at buttons they cannot
     * press, and a user without one must not be left with no way to answer Big Picture.
     */
    private val deviceListener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) = updateOnScreenControls()
        override fun onInputDeviceRemoved(deviceId: Int) = updateOnScreenControls()
        override fun onInputDeviceChanged(deviceId: Int) = updateOnScreenControls()
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
            // A session already running is past its milestones; do not cover its picture.
            visibility = if (SessionState.running) View.GONE else View.VISIBLE
        }
        root.addView(statusView)
        setContentView(root)

        val bridge = PadBridge(File(LinuxRuntime.sessionRoot(this), "dev/input"))
        padBridge = bridge
        onScreenControls = OnScreenControls(this, bridge).also { root.addView(it, 1) }
        updateOnScreenControls()
        WaylandCompositor.setFirstFrameListener { runOnUiThread { statusView.visibility = View.GONE } }
        SessionState.endListener = { status -> onSessionEnded(status) }
        watchSessionLog()
    }

    // ── Compositor ──────────────────────────────────────────────────────────────────────────

    override fun surfaceCreated(holder: SurfaceHolder) {
        val runtimeDir = File(filesDir, ".wayland-rt").apply { mkdirs() }
        // The compositor hands this keymap to wl_keyboard clients, which is how the guest reads
        // the evdev codes we inject.
        FileUtils.copyAsset(this, "wayland/keymap.xkb", File(runtimeDir, "keymap.xkb"))

        // Turnip, not the system Adreno driver: importing the dma-bufs gamescope commits needs
        // VK_EXT_image_drm_format_modifier, which the system driver does not implement.
        val turnip = TurnipDriver(this)
        val driverId = if (CompositorHost.isStarted) null else turnip.install()

        val size = outputSize()
        SessionState.outputSize = size
        SessionState.refreshHz = refreshHz()
        val first = CompositorHost.startOrAttach(
            holder.surface, runtimeDir.path,
            driverId?.let { turnip.driverPath(it) }, driverId?.let { turnip.libraryName(it) },
            applicationInfo.nativeLibraryDir, size.first, size.second, refreshHz(),
        )
        // The service owns everything below the compositor, and starting it is idempotent: coming
        // back to a running session just re-attaches the Surface above.
        if (first) SessionService.start(this)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        CompositorHost.detach()
    }

    /**
     * The size the session renders at. gamescope is told this and scales its output onto whatever
     * the panel is, so a 1440p phone can run the client at 1080p without the client knowing.
     */
    private fun outputSize(): Pair<Int, Int> {
        // The panel, not the window: resources.displayMetrics is what is left after the system
        // bars and the cutout are taken out, which sized the first session 1920x968 on a 1080p
        // device and had gamescope patch its EDID to match.
        val bounds = if (Build.VERSION.SDK_INT >= 30) {
            windowManager.maximumWindowMetrics.bounds
        } else {
            val metrics = android.util.DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)
            android.graphics.Rect(0, 0, metrics.widthPixels, metrics.heightPixels)
        }
        var width = maxOf(bounds.width(), bounds.height())
        var height = minOf(bounds.width(), bounds.height())
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

    // ── Session state ───────────────────────────────────────────────────────────────────────

    /** Mirrors the session script's "== STEP" milestones onto the screen while it starts. */
    private fun watchSessionLog() {
        val handler = Handler(Looper.getMainLooper())
        Thread({
            var shown = ""
            while (watching) {
                try {
                    Thread.sleep(500)
                    val log = SessionState.logFile ?: continue
                    val text = FileUtils.readString(log) ?: continue
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

    private fun onSessionEnded(status: Int) {
        runOnUiThread {
            if (isFinishing || isDestroyed) return@runOnUiThread
            if (status != 0) {
                statusView.visibility = View.VISIBLE
                statusView.text = getString(R.string.session_ended, status,
                    SessionState.logFile?.path ?: "-")
                // A moment on screen, so a failure is readable rather than a flash of black.
                Handler(Looper.getMainLooper()).postDelayed({ finish() }, 4000)
            } else {
                finish()
            }
        }
    }

    // ── Input ───────────────────────────────────────────────────────────────────────────────

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (padBridge?.onKeyEvent(event) == true) return true
        // A hardware keyboard, forwarded to the compositor's wl_keyboard. This is how an account
        // name and password get typed on the client's first run; the client's own on-screen
        // keyboard covers a device without one, driven by touch or the pad.
        if (CompositorHost.isStarted && event.device != null && !PadBridge.isFromController(event.device)) {
            val down = event.action == KeyEvent.ACTION_DOWN
            if (down || event.action == KeyEvent.ACTION_UP) {
                // An unmapped key may still be a real one on a foreign layout: its scan code is
                // the evdev code the kernel gave Android in the first place.
                var evdev = EvdevKeys.fromKeyCode(event.keyCode)
                if (evdev < 0 && event.scanCode > 0) evdev = event.scanCode
                if (evdev > 0) {
                    WaylandCompositor.nativeSendKey(evdev, if (down) 1 else 0)
                    return true
                }
            }
        }
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

    /**
     * Back leaves the session running and puts the app behind whatever the user wants next; the
     * notification brings it back. Ending a session is done deliberately — from the
     * notification's Stop action, or by swiping the app out of recents.
     */
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        moveTaskToBack(true)
    }

    /**
     * `steamdeck-osc` in Downloads forces the matter either way ("always" / "never"); with no such
     * file the controls follow what is attached.
     */
    private fun updateOnScreenControls() {
        val forced = File(Environment.getExternalStorageDirectory(), "Download/steamdeck-osc")
            .takeIf { it.isFile }
            ?.let { FileUtils.readString(it)?.trim()?.lowercase() }
        val show = when {
            forced == "always" -> true
            forced == "never" -> false
            else -> !PadBridge.anyControllerConnected()
        }
        val controls = onScreenControls ?: return
        if (show == (controls.visibility == View.VISIBLE)) return
        if (!show) controls.releaseAll()
        controls.visibility = if (show) View.VISIBLE else View.GONE
        Log.i(TAG, "on-screen controls " + (if (show) "shown" else "hidden"))
    }

    override fun onResume() {
        super.onResume()
        (getSystemService(INPUT_SERVICE) as? InputManager)
            ?.registerInputDeviceListener(deviceListener, Handler(Looper.getMainLooper()))
        updateOnScreenControls()
    }

    override fun onPause() {
        (getSystemService(INPUT_SERVICE) as? InputManager)?.unregisterInputDeviceListener(deviceListener)
        // A button held when the app goes away would stay held in the ring for the whole session.
        onScreenControls?.releaseAll()
        super.onPause()
    }

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
        // Deliberately does NOT end the session: this activity can be destroyed while the user is
        // in another app, and the whole point of the service is that Steam survives that.
        watching = false
        padBridge?.stop()
        SessionState.endListener = null
        WaylandCompositor.setFirstFrameListener(null)
        super.onDestroy()
    }

    companion object {
        private const val TAG = "SessionActivity"
    }
}
