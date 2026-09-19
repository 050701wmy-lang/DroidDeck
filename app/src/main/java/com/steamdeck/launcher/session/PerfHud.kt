package com.steamdeck.launcher.session

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import com.steamdeck.launcher.gpu.FrameGen
import com.steamdeck.launcher.wayland.WaylandCompositor
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * A line of numbers in the top-right corner: the game's own frame rate and, when frame generation
 * is on, what the screen is actually being shown — "60 → 118 fps" is the proof that the engine is
 * doing something, and "FG starting" or a reason is the proof that it is not.
 *
 * The base rate is counted here from the compositor's per-frame callback, so it is right with or
 * without an engine. The presented rate comes from the engine's own telemetry while it generates.
 * `steamdeck-no-hud` in Downloads hides it.
 */
class PerfHud(context: Context) {
    val view: TextView = TextView(context).apply {
        typeface = Typeface.MONOSPACE
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        setTextColor(Color.WHITE)
        setBackgroundColor(Color.argb(140, 0, 0, 0))
        setPadding(14, 6, 14, 6)
        // Numbers only; every touch goes through to the game.
        isClickable = false
        isFocusable = false
        visibility = View.GONE
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.END,
        ).apply { topMargin = 12; rightMargin = 12 }
    }

    private val context = context.applicationContext
    private val frames = AtomicInteger()
    private var lastTick = 0L
    private var running = false
    private val handler = Handler(Looper.getMainLooper())
    private val enabled = !File(Environment.getExternalStorageDirectory(), "Download/steamdeck-no-hud").exists()

    private val listener = object : WaylandCompositor.GameListener {
        override fun onGameSurface(window: String?, gpuName: String?) {}
        override fun onGameFrame() { frames.incrementAndGet() }
    }

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            val now = SystemClock.elapsedRealtime()
            val dt = (now - lastTick).coerceAtLeast(1L) / 1000f
            lastTick = now
            val base = frames.getAndSet(0) / dt
            view.text = line(base)
            handler.postDelayed(this, 1000)
        }
    }

    fun start() {
        if (!enabled || running) return
        running = true
        lastTick = SystemClock.elapsedRealtime()
        frames.set(0)
        WaylandCompositor.setGameListener(listener)
        view.visibility = View.VISIBLE
        handler.post(tick)
    }

    fun stop() {
        running = false
        WaylandCompositor.setGameListener(null)
        view.visibility = View.GONE
    }

    private fun line(base: Float): String {
        val engine = FrameGen.engine(context)
        if (engine == FrameGen.ENGINE_OFF) return fps(base)
        val name = if (engine == FrameGen.ENGINE_LSFG) "LSFG" else "Win-FG"
        val multiplier = FrameGen.multiplier(context)
        val stats = try { WaylandCompositor.nativeFrameGenStats() } catch (t: Throwable) { null }
        val problem = try { WaylandCompositor.nativeFrameGenProblem() } catch (t: Throwable) { -1 }
        return when {
            problem == 1 -> "$name: unsupported"
            problem == 2 -> "$name: engine failed"
            stats != null && stats[3] > 1f -> {
                val source = if (stats[2] > 1f) stats[2] else base
                String.format(Locale.US, "%s %d×  %.0f → %.0f fps", name, multiplier, source, stats[3])
            }
            else -> String.format(Locale.US, "%s %d×  %s · FG starting", name, multiplier, fps(base))
        }
    }

    private fun fps(value: Float) = String.format(Locale.US, "%.0f fps", value)
}
