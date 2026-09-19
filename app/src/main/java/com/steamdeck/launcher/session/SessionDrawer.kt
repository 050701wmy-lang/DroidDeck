package com.steamdeck.launcher.session

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Switch
import com.steamdeck.launcher.R
import com.steamdeck.launcher.gpu.FrameGen

/**
 * The drawer Back opens over a running session. It owns nothing: every control reads and writes
 * a preference or calls back into the activity, so closing it changes nothing by itself.
 */
class SessionDrawer(
    private val activity: Activity,
    parent: ViewGroup,
    private val onHudChanged: (Boolean) -> Unit,
    private val onOscChanged: () -> Unit,
    private val onFrameGenChanged: () -> Unit,
    private val onBackground: () -> Unit,
    private val onStop: () -> Unit,
) {
    private val root: View = activity.layoutInflater.inflate(R.layout.session_drawer, parent, false)
    private val hud = root.findViewById<Switch>(R.id.drawer_hud)
    private val frameGen = root.findViewById<Button>(R.id.drawer_frame_gen)
    private val osc = root.findViewById<Button>(R.id.drawer_osc)
    private val shape = root.findViewById<Button>(R.id.drawer_shape)

    init {
        root.visibility = View.GONE
        parent.addView(root)
        // The dimmed area closes; the panel itself swallows its touches.
        root.setOnClickListener { close() }
        root.findViewById<View>(R.id.drawer_panel).setOnClickListener { }
        hud.setOnCheckedChangeListener { _, on ->
            SessionPrefs.setHudEnabled(activity, on)
            onHudChanged(on)
        }
        frameGen.setOnClickListener {
            FrameGen.showPicker(activity) {
                refresh()
                onFrameGenChanged()
            }
        }
        osc.setOnClickListener {
            val next = when (SessionPrefs.oscMode(activity)) {
                SessionPrefs.OSC_AUTO -> SessionPrefs.OSC_ALWAYS
                SessionPrefs.OSC_ALWAYS -> SessionPrefs.OSC_NEVER
                else -> SessionPrefs.OSC_AUTO
            }
            SessionPrefs.setOscMode(activity, next)
            refresh()
            onOscChanged()
        }
        shape.setOnClickListener {
            val next = if (SessionPrefs.shapeMode(activity) == SessionPrefs.SHAPE_WIDE) SessionPrefs.SHAPE_AUTO
                       else SessionPrefs.SHAPE_WIDE
            SessionPrefs.setShapeMode(activity, next)
            refresh()
        }
        root.findViewById<View>(R.id.drawer_background).setOnClickListener {
            close()
            onBackground()
        }
        root.findViewById<View>(R.id.drawer_stop).setOnClickListener {
            close()
            onStop()
        }
    }

    val isOpen: Boolean get() = root.visibility == View.VISIBLE

    fun open() {
        refresh()
        root.visibility = View.VISIBLE
        root.bringToFront()
    }

    fun close() {
        root.visibility = View.GONE
    }

    fun toggle() = if (isOpen) close() else open()

    private fun refresh() {
        hud.isChecked = SessionPrefs.hudEnabled(activity)
        frameGen.text = activity.getString(R.string.frame_gen, FrameGen.label(activity))
        val mode = when (SessionPrefs.oscMode(activity)) {
            SessionPrefs.OSC_ALWAYS -> activity.getString(R.string.osc_always)
            SessionPrefs.OSC_NEVER -> activity.getString(R.string.osc_never)
            else -> activity.getString(R.string.osc_auto)
        }
        osc.text = activity.getString(R.string.drawer_osc, mode)
        shape.text = activity.getString(R.string.drawer_shape,
            if (SessionPrefs.shapeMode(activity) == SessionPrefs.SHAPE_WIDE) activity.getString(R.string.shape_wide)
            else activity.getString(R.string.shape_auto))
    }
}
