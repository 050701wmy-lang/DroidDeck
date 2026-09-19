package com.steamdeck.launcher

import android.hardware.input.InputManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import com.steamdeck.launcher.core.FileUtils
import com.steamdeck.launcher.gpu.FrameGen
import com.steamdeck.launcher.gpu.LsfgNative
import com.steamdeck.launcher.gpu.TurnipDriver
import com.steamdeck.launcher.input.EvdevKeys
import com.steamdeck.launcher.input.OnScreenControls
import com.steamdeck.launcher.input.PadBridge
import com.steamdeck.launcher.runtime.LinuxRuntime
import com.steamdeck.launcher.session.LoadingState
import com.steamdeck.launcher.session.PerfHud
import com.steamdeck.launcher.session.SessionPrefs
import com.steamdeck.launcher.session.SessionService
import com.steamdeck.launcher.session.SessionState
import com.steamdeck.launcher.ui.DrawerActions
import com.steamdeck.launcher.ui.FrameGenDialog
import com.steamdeck.launcher.ui.HudText
import com.steamdeck.launcher.ui.LoadingOverlay
import com.steamdeck.launcher.ui.SessionDrawer
import com.steamdeck.launcher.ui.SteamDeckTheme
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
 * Three layers: the SurfaceView the compositor draws into, the on-screen pad (a canvas View,
 * since it is input rather than a menu), and one Compose layer on top for everything else —
 * the HUD line, the loading overlay, the drawer and its dialogs.
 */
class SessionActivity : ComponentActivity(), SurfaceHolder.Callback {
    private lateinit var surfaceView: SurfaceView
    private lateinit var loading: LoadingState
    private lateinit var hud: PerfHud
    private var padBridge: PadBridge? = null
    private var onScreenControls: OnScreenControls? = null
    private var watching = true

    // Compose reads these; the activity writes them.
    private var drawerOpen by mutableStateOf(false)
    private var showFrameGen by mutableStateOf(false)
    private var hudOn by mutableStateOf(true)
    private var frameGenLabel by mutableStateOf("Off")
    private var oscMode by mutableStateOf(SessionPrefs.OSC_AUTO)
    private var shapeMode by mutableStateOf(SessionPrefs.SHAPE_AUTO)

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

        val bridge = PadBridge(File(LinuxRuntime.sessionRoot(this), "dev/input"))
        padBridge = bridge
        onScreenControls = OnScreenControls(this, bridge).also { root.addView(it) }

        loading = LoadingState(this)
        hud = PerfHud(this)
        hud.onPresentingWindowChanged = {
            if (FrameGen.engine(this) != FrameGen.ENGINE_OFF) CompositorHost.rearmFrameGen { applyFrameGen() }
        }
        // A session that has already drawn is past its milestones; do not cover its picture.
        if (SessionState.running && SessionState.firstFrameSeen) {
            loading.visible = false
            hud.start()
        }
        readPrefs()

        // One Compose layer for every menu and overlay. Touches nothing in it consumes fall
        // through to the pad and the game underneath.
        root.addView(ComposeView(this).apply {
            setContent {
                SteamDeckTheme {
                    if (hud.text.isNotEmpty()) HudText(hud.text)
                    if (loading.visible) LoadingOverlay(loading.step, loading.percent, loading.elapsed, loading.hint, loading.ended)
                    if (drawerOpen) SessionDrawer(DrawerActions(
                        hudOn = hudOn, frameGenLabel = frameGenLabel, oscMode = oscMode,
                        shapeMode = if (shapeMode == SessionPrefs.SHAPE_WIDE) "16:9" else "panel",
                        onHud = { on -> SessionPrefs.setHudEnabled(this@SessionActivity, on); hudOn = on; hud.refresh() },
                        onFrameGen = { showFrameGen = true },
                        onOsc = {
                            val next = when (SessionPrefs.oscMode(this@SessionActivity)) {
                                SessionPrefs.OSC_AUTO -> SessionPrefs.OSC_ALWAYS
                                SessionPrefs.OSC_ALWAYS -> SessionPrefs.OSC_NEVER
                                else -> SessionPrefs.OSC_AUTO
                            }
                            SessionPrefs.setOscMode(this@SessionActivity, next)
                            readPrefs()
                            updateOnScreenControls()
                        },
                        onShape = {
                            val next = if (SessionPrefs.shapeMode(this@SessionActivity) == SessionPrefs.SHAPE_WIDE)
                                SessionPrefs.SHAPE_AUTO else SessionPrefs.SHAPE_WIDE
                            SessionPrefs.setShapeMode(this@SessionActivity, next)
                            readPrefs()
                        },
                        onBackground = { drawerOpen = false; moveTaskToBack(true) },
                        onStop = { drawerOpen = false; SessionService.stop(this@SessionActivity); finish() },
                        onClose = { drawerOpen = false },
                    ))
                    if (showFrameGen) FrameGenDialog(
                        engine = FrameGen.engine(this@SessionActivity),
                        multiplier = FrameGen.multiplier(this@SessionActivity),
                        lsfgReady = LsfgNative.isInstalled(this@SessionActivity),
                        onPick = { engine, multiplier ->
                            FrameGen.set(this@SessionActivity, engine, multiplier)
                            showFrameGen = false
                            readPrefs()
                            applyFrameGen()
                        },
                        onDismiss = { showFrameGen = false },
                    )
                }
            }
        })
        setContentView(root)

        updateOnScreenControls()
        WaylandCompositor.setFirstFrameListener {
            SessionState.firstFrameSeen = true
            runOnUiThread {
                loading.visible = false
                hud.start()
            }
        }
        SessionState.endListener = { status -> onSessionEnded(status) }
        // Back opens the drawer (and closes it again). Leaving the session running in the
        // background and ending it are both actions in there, so neither can happen by accident
        // from a button a game might also be reading.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (showFrameGen) showFrameGen = false else drawerOpen = !drawerOpen
            }
        })
        watchSession()
    }

    private fun readPrefs() {
        hudOn = SessionPrefs.hudEnabled(this)
        frameGenLabel = FrameGen.label(this)
        oscMode = SessionPrefs.oscMode(this)
        shapeMode = SessionPrefs.shapeMode(this)
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

        // The output size belongs to the session, not to the Surface: gamescope's display is
        // sized once when the session starts and cannot change. A foldable recreates the Surface
        // on the other panel, and recomputing the size there told the compositor a 21:9 buffer
        // was 16:9 — the picture came back squashed sideways. While a session runs, keep its size.
        val size = if (SessionState.running) SessionState.outputSize else outputSize()
        SessionState.outputSize = size
        if (!SessionState.running) SessionState.refreshHz = refreshHz()
        // Letterbox, never stretch or crop: the output can be a different shape from the panel,
        // and a game's picture must keep its proportions with bars, not lose its edges.
        WaylandCompositor.nativeSetScaleMode(SCALE_FIT, ALIGN_CENTER)
        CompositorHost.startOrAttach(
            holder.surface, runtimeDir.path,
            driverId?.let { turnip.driverPath(it) }, driverId?.let { turnip.libraryName(it) },
            applicationInfo.nativeLibraryDir, size.first, size.second, refreshHz(),
        )
        // The service owns everything below the compositor. It is started whenever no session is
        // running — NOT only when the compositor was just started: the compositor lives for the
        // whole process, so the second Play after a session ended used to re-attach the Surface,
        // start nothing, and leave the loading panel counting up over a dead session.
        if (!SessionState.running) {
            CompositorHost.newSession()
            SessionService.start(this)
        }
        applyFrameGen()
    }

    private var surfaceW = 0
    private var surfaceH = 0

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        val resized = surfaceW != 0 && (width != surfaceW || height != surfaceH)
        surfaceW = width
        surfaceH = height
        if (resized) {
            Log.i(TAG, "surface resized to ${width}x$height — rebinding the compositor")
            CompositorHost.resize(holder.surface) { applyFrameGen() }
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        surfaceW = 0
        surfaceH = 0
        CompositorHost.detach()
    }

    /**
     * The size the session renders at. gamescope is told this and scales its output onto whatever
     * the panel is, so a 1440p phone can run the client at 1080p without the client knowing.
     */
    private fun outputSize(): Pair<Int, Int> {
        // The panel, not the window: resources.displayMetrics is what is left after the system
        // bars and the cutout are taken out.
        val bounds = if (Build.VERSION.SDK_INT >= 30) {
            windowManager.maximumWindowMetrics.bounds
        } else {
            val metrics = android.util.DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)
            android.graphics.Rect(0, 0, metrics.widthPixels, metrics.heightPixels)
        }
        val panelW = maxOf(bounds.width(), bounds.height()).toFloat()
        val panelH = minOf(bounds.width(), bounds.height()).toFloat()
        // Never narrower than 16:9. A foldable's inner panel is nearly square, and a game handed a
        // square display draws for the frame it was made for and cuts the sides off itself.
        // Wider than 16:9 is fine — games and the client cope with a phone's 20:9 — so the
        // panel's aspect is kept above that, unless the user pinned 16:9 for a foldable, and the
        // compositor letterboxes onto a squarer panel.
        val aspect = if (SessionPrefs.shapeMode(this) == SessionPrefs.SHAPE_WIDE) 16f / 9f
                     else maxOf(panelW / panelH, 16f / 9f)
        // 1080 tall at most: the client's CEF is the heaviest thing in the session, and above
        // 1080p it costs frames for nothing anyone can see on a handheld panel.
        val height = minOf(panelH, 1080f).toInt()
        val width = (height * aspect).toInt()
        // Odd sizes upset the scaler; both dimensions even is what every mode here would be.
        return Pair(width and 1.inv(), height and 1.inv())
    }

    private fun refreshHz(): Float {
        val display = if (Build.VERSION.SDK_INT >= 30) display else windowManager.defaultDisplay
        val hz = display?.refreshRate ?: 60f
        return if (hz > 1f) hz else 60f
    }

    /**
     * The saved frame-generation setting, pushed to the compositor. Off the main thread: LSFG's
     * first use translates the shader chain out of Lossless.dll, which takes seconds.
     */
    private fun applyFrameGen() {
        val hz = refreshHz()
        Thread({
            val problem = FrameGen.apply(this, hz)
            if (problem != null) runOnUiThread { Toast.makeText(this, problem, Toast.LENGTH_LONG).show() }
        }, "frame-gen").start()
    }

    // ── Session state ───────────────────────────────────────────────────────────────────────

    /** Keeps the loading overlay current from the session log, and its clock moving. */
    private fun watchSession() {
        val handler = Handler(Looper.getMainLooper())
        var ticks = 0
        val poll = object : Runnable {
            override fun run() {
                if (!watching) return
                if (loading.visible && !loading.ended) {
                    loading.update(this@SessionActivity, SessionState.logFile)
                    if (ticks++ % 2 == 0) loading.tick()
                }
                handler.postDelayed(this, 500)
            }
        }
        handler.post(poll)
    }

    private fun onSessionEnded(status: Int) {
        runOnUiThread {
            if (isFinishing || isDestroyed) return@runOnUiThread
            if (status != 0) {
                loading.showEnded("The session ended ($status)\n${SessionState.logFile?.path ?: "-"}")
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
        // A hardware keyboard, forwarded to the compositor's wl_keyboard. Back is left to the
        // activity, which opens the drawer.
        if (CompositorHost.isStarted && event.keyCode != KeyEvent.KEYCODE_BACK
            && event.device != null && !PadBridge.isFromController(event.device)) {
            val down = event.action == KeyEvent.ACTION_DOWN
            if (down || event.action == KeyEvent.ACTION_UP) {
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
     * The compositor's pointer space is a fixed 1920x1080, and the picture is letterboxed inside
     * the view when the panel is a different shape from the output, so a touch is mapped through
     * the fitted rectangle.
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
        val out = SessionState.outputSize
        val scale = minOf(width / out.first.toFloat(), height / out.second.toFloat())
        val drawnW = out.first * scale
        val drawnH = out.second * scale
        val left = (width - drawnW) / 2f
        val top = (height - drawnH) / 2f
        val x = ((event.x - left) / drawnW * 1920f).toInt().coerceIn(0, 1919)
        val y = ((event.y - top) / drawnH * 1080f).toInt().coerceIn(0, 1079)
        WaylandCompositor.nativeSendPointer(action, x, y)
        return true
    }

    /**
     * The drawer's mode ("always" / "never") decides outright; on "auto" the controls follow
     * what is attached. `steamdeck-osc` in Downloads still overrides, for a device we cannot reach.
     */
    private fun updateOnScreenControls() {
        val forced = File(Environment.getExternalStorageDirectory(), "Download/steamdeck-osc")
            .takeIf { it.isFile }
            ?.let { FileUtils.readString(it)?.trim()?.lowercase() }
            ?: SessionPrefs.oscMode(this)
        val show = when (forced) {
            SessionPrefs.OSC_ALWAYS -> true
            SessionPrefs.OSC_NEVER -> false
            else -> !PadBridge.anyControllerConnected()
        }
        val controls = onScreenControls ?: return
        if (show == (controls.visibility == View.VISIBLE)) return
        if (!show) controls.releaseAll()
        controls.visibility = if (show) View.VISIBLE else View.GONE
        Log.i(TAG, "on-screen controls " + (if (show) "shown" else "hidden"))
    }

    // ── Lifecycle ───────────────────────────────────────────────────────────────────────────

    override fun onResume() {
        super.onResume()
        (getSystemService(INPUT_SERVICE) as? InputManager)
            ?.registerInputDeviceListener(deviceListener, Handler(Looper.getMainLooper()))
        updateOnScreenControls()
        readPrefs()
        if (CompositorHost.isStarted) applyFrameGen()
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
        if (::hud.isInitialized) hud.stop()
        padBridge?.stop()
        SessionState.endListener = null
        WaylandCompositor.setFirstFrameListener(null)
        super.onDestroy()
    }

    companion object {
        private const val TAG = "SessionActivity"
        /** Compositor scale modes (Container.FULLSCREEN_* values): 1 = fit with bars, centred. */
        private const val SCALE_FIT = 1
        private const val ALIGN_CENTER = 0
    }
}
