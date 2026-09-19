package com.steamdeck.launcher.input

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View

/**
 * A pad drawn on the screen for devices that have no controller attached.
 *
 * It writes into the same [PadBridge] a physical pad does, so the Steam client sees one Xbox pad
 * whichever is being used, and the two can be used at once. The layout is fixed and deliberately
 * small: Big Picture needs a d-pad, A and B, a menu button and the Steam button, and anything
 * more would be a control editor rather than a way to sign in and start a game.
 *
 * Only the controls themselves take touches; everything else falls through to the activity, where
 * it moves the mouse pointer.
 */
@SuppressLint("ViewConstructor")
class OnScreenControls(context: Context, private val pad: PadBridge) : View(context) {

    private class Control(
        val id: String,
        val label: String,
        /** Bit index in GamepadState, or -1 for a d-pad direction. */
        val button: Int,
        /** Index into GamepadState.dpad, or -1. */
        val dpad: Int,
        val radius: Float,
    ) {
        var cx = 0f
        var cy = 0f
        var pressedBy = -1     // the pointer id holding it, or -1
        val bounds = RectF()
        fun contains(x: Float, y: Float): Boolean {
            val dx = x - cx
            val dy = y - cy
            // A generous hit area: a finger on glass is not a mouse, and a miss in Big Picture
            // means the user thinks the pad does not work.
            val r = radius * 1.25f
            return dx * dx + dy * dy <= r * r
        }
    }

    private val density = resources.displayMetrics.density
    private fun dp(value: Float) = value * density

    private val controls = listOf(
        Control("up", "▲", -1, 0, dp(26f)),
        Control("right", "▶", -1, 1, dp(26f)),
        Control("down", "▼", -1, 2, dp(26f)),
        Control("left", "◀", -1, 3, dp(26f)),
        Control("a", "A", 0, -1, dp(30f)),
        Control("b", "B", 1, -1, dp(30f)),
        Control("x", "X", 2, -1, dp(30f)),
        Control("y", "Y", 3, -1, dp(30f)),
        Control("lb", "LB", 4, -1, dp(24f)),
        Control("rb", "RB", 5, -1, dp(24f)),
        Control("select", "⧉", 6, -1, dp(20f)),
        Control("start", "☰", 7, -1, dp(20f)),
        // The client's own in-game menu; the interposer publishes it as BTN_MODE.
        Control("guide", "◉", GamepadState.IDX_BUTTON_MODE.toInt(), -1, dp(22f)),
    )

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layoutControls(w.toFloat(), h.toFloat())
    }

    /** Thumbs reach the bottom corners; nothing is placed where a game's own HUD usually is. */
    private fun layoutControls(w: Float, h: Float) {
        val margin = dp(34f)
        val dpadX = margin + dp(74f)
        val dpadY = h - margin - dp(74f)
        val step = dp(52f)
        place("up", dpadX, dpadY - step)
        place("down", dpadX, dpadY + step)
        place("left", dpadX - step, dpadY)
        place("right", dpadX + step, dpadY)

        val faceX = w - margin - dp(74f)
        val faceY = h - margin - dp(74f)
        val faceStep = dp(50f)
        place("a", faceX, faceY + faceStep)
        place("b", faceX + faceStep, faceY)
        place("x", faceX - faceStep, faceY)
        place("y", faceX, faceY - faceStep)

        place("lb", margin + dp(46f), h - margin - dp(196f))
        place("rb", w - margin - dp(46f), h - margin - dp(196f))

        val centre = w / 2f
        place("select", centre - dp(78f), h - margin - dp(26f))
        place("guide", centre, h - margin - dp(26f))
        place("start", centre + dp(78f), h - margin - dp(26f))
    }

    private fun place(id: String, x: Float, y: Float) {
        val control = controls.firstOrNull { it.id == id } ?: return
        control.cx = x
        control.cy = y
        control.bounds.set(x - control.radius, y - control.radius, x + control.radius, y + control.radius)
    }

    override fun onDraw(canvas: Canvas) {
        for (control in controls) {
            val held = control.pressedBy != -1
            fill.color = if (held) Color.argb(150, 199, 125, 255) else Color.argb(70, 20, 12, 30)
            canvas.drawCircle(control.cx, control.cy, control.radius, fill)
            stroke.color = if (held) Color.argb(220, 235, 212, 255) else Color.argb(110, 201, 160, 255)
            canvas.drawCircle(control.cx, control.cy, control.radius, stroke)
            text.color = if (held) Color.WHITE else Color.argb(190, 225, 210, 245)
            text.textSize = control.radius * 0.85f
            canvas.drawText(control.label, control.cx, control.cy + text.textSize * 0.35f, text)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val index = event.actionIndex
                val control = controlAt(event.getX(index), event.getY(index)) ?: return false
                control.pressedBy = event.getPointerId(index)
                apply()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                // A finger that slides off a button releases it, and one that slides onto another
                // presses that — which is how a d-pad is used in practice.
                var changed = false
                for (index in 0 until event.pointerCount) {
                    val pointer = event.getPointerId(index)
                    val over = controlAt(event.getX(index), event.getY(index))
                    for (control in controls) {
                        if (control.pressedBy == pointer && control !== over) {
                            control.pressedBy = -1
                            changed = true
                        }
                    }
                    if (over != null && over.pressedBy == -1) {
                        over.pressedBy = pointer
                        changed = true
                    }
                }
                if (changed) apply()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                val pointer = event.getPointerId(event.actionIndex)
                var changed = false
                for (control in controls) {
                    if (control.pressedBy == pointer || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                        if (control.pressedBy != -1) changed = true
                        control.pressedBy = -1
                    }
                }
                if (changed) apply()
                return true
            }
        }
        return false
    }

    private fun controlAt(x: Float, y: Float): Control? = controls.firstOrNull { it.contains(x, y) }

    private fun apply() {
        pad.applyTouch { state ->
            for (control in controls) {
                val held = control.pressedBy != -1
                if (control.dpad >= 0) state.dpad[control.dpad] = held
                else if (control.button >= 0) state.setPressed(control.button, held)
            }
        }
        invalidate()
    }

    /** Everything up, for when the controls are hidden mid-press. */
    fun releaseAll() {
        if (controls.none { it.pressedBy != -1 }) return
        controls.forEach { it.pressedBy = -1 }
        apply()
    }
}
