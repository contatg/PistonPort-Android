// SPDX-License-Identifier: GPL-2.0-or-later
package org.pistonport.nativeandroid

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
import android.util.SparseArray
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Adapted from the 1280×720, per-pointer touch routing and drag editor in
 * niilo/gow3_android (046791de), TouchControllerOverlay.java.
 * PistonPort uses SDL key bindings and a layout for racing.
 */
internal class RaceControls(
    context: Context,
    scale: Int,
    private val opacity: Int,
    private val onKey: (Int, Boolean) -> Unit,
) : View(context) {
    private data class Control(val id: String, val text: String, val key: Int,
        val x: Float, val y: Float, val wide: Boolean = false)

    private val controls = listOf(
        Control("steer", "", 0, 155f, 540f),
        Control("jump", "×", KeyEvent.KEYCODE_SPACE, 1090f, 530f),
        Control("drift", "○", KeyEvent.KEYCODE_SHIFT_LEFT, 1172f, 454f),
        Control("item", "△", KeyEvent.KEYCODE_Q, 1090f, 378f),
        Control("boost", "□", KeyEvent.KEYCODE_E, 1008f, 454f),
        Control("brake", "FREIO", KeyEvent.KEYCODE_R, 955f, 637f, true),
        Control("gas", "ACELERAR", KeyEvent.KEYCODE_F, 1145f, 637f, true),
        Control("pause", "Ⅱ", KeyEvent.KEYCODE_ENTER, 1200f, 68f),
    )
    private val settings = context.getSharedPreferences("touch-layout", Context.MODE_PRIVATE)
    private val centers = controls.associateWith {
        PointF(settings.getFloat("${it.id}_x", it.x), settings.getFloat("${it.id}_y", it.y))
    }
    private val fingers = SparseArray<Control>()
    private val positions = SparseArray<PointF>()
    private var pressed = emptySet<Int>()
    private var dragged: Control? = null
    private var editorPointer = -1
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bounds = RectF()
    private val size = scale / 100f
    private val typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)

    var editing = false
        set(value) {
            release()
            field = value
            invalidate()
        }

    init {
        contentDescription = "Controles touch do PistonPort"
    }

    private fun zoom() = min(width / 1280f, height / 720f).coerceAtLeast(.001f)
    private fun offsetX() = (width - 1280 * zoom()) / 2
    private fun offsetY() = (height - 720 * zoom()) / 2
    private fun x(event: MotionEvent, index: Int) = (event.getX(index) - offsetX()) / zoom()
    private fun y(event: MotionEvent, index: Int) = (event.getY(index) - offsetY()) / zoom()

    private fun hit(x: Float, y: Float): Control? = controls.filter {
        val center = centers.getValue(it)
        if (it.wide) abs(x - center.x) <= 82 * size && abs(y - center.y) <= 31 * size
        else hypot(x - center.x, y - center.y) <= (if (it.key == 0) 113 else 39) * size
    }.minByOrNull { hypot(x - centers.getValue(it).x, y - centers.getValue(it).y) }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        val index = event.actionIndex
        val pointer = if (event.pointerCount > 0) event.getPointerId(index) else -1
        when (action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val control = hit(x(event, index), y(event, index))
                if (action == MotionEvent.ACTION_DOWN && control == null) return false
                if (editing && editorPointer < 0 && control != null) {
                    dragged = control
                    editorPointer = pointer
                } else if (!editing && control != null) fingers.put(pointer, control)
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                fingers.remove(pointer)
                positions.remove(pointer)
                if (pointer == editorPointer) {
                    dragged?.let {
                        val point = centers.getValue(it)
                        settings.edit().putFloat("${it.id}_x", point.x)
                            .putFloat("${it.id}_y", point.y).apply()
                    }
                    dragged = null
                    editorPointer = -1
                }
                if (action == MotionEvent.ACTION_UP) {
                    release()
                    parent?.requestDisallowInterceptTouchEvent(false)
                    performClick()
                    return true
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                release()
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        if (editing) {
            val active = event.findPointerIndex(editorPointer)
            val control = dragged
            if (control != null && active >= 0) {
                val radiusX = (if (control.key == 0) 113 else if (control.wide) 82 else 39) * size
                val radiusY = (if (control.key == 0) 113 else if (control.wide) 31 else 39) * size
                centers.getValue(control).set(x(event, active).coerceIn(radiusX, 1280 - radiusX),
                    y(event, active).coerceIn(radiusY, 720 - radiusY))
            }
        } else {
            for (i in 0 until event.pointerCount) {
                val id = event.getPointerId(i)
                if (fingers[id] != null) {
                    val point = positions[id] ?: PointF().also { positions.put(id, it) }
                    point.set(x(event, i), y(event, i))
                }
            }
            publish()
        }
        invalidate()
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    private fun publish() {
        val next = buildSet {
            for (i in 0 until fingers.size()) {
                val control = fingers.valueAt(i)
                if (control.key != 0) {
                    add(control.key)
                    continue
                }
                val point = positions[fingers.keyAt(i)] ?: continue
                val center = centers.getValue(control)
                val dead = 26 * size
                if (point.x < center.x - dead) add(KeyEvent.KEYCODE_A)
                if (point.x > center.x + dead) add(KeyEvent.KEYCODE_D)
                if (point.y < center.y - dead) add(KeyEvent.KEYCODE_W)
                if (point.y > center.y + dead) add(KeyEvent.KEYCODE_S)
            }
        }
        (pressed - next).forEach { onKey(it, false) }
        (next - pressed).forEach { onKey(it, true) }
        pressed = next
    }

    fun release() {
        fingers.clear()
        positions.clear()
        dragged = null
        editorPointer = -1
        publish()
        invalidate()
    }

    fun resetLayout() {
        settings.edit().clear().apply()
        for (control in controls) centers.getValue(control).set(control.x, control.y)
        release()
    }

    override fun onDetachedFromWindow() {
        release()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val z = zoom()
        canvas.save()
        canvas.translate(offsetX(), offsetY())
        canvas.scale(z, z)
        for (control in controls) {
            val center = centers.getValue(control)
            canvas.save()
            canvas.translate(center.x, center.y)
            canvas.scale(size, size)
            if (control.key == 0) {
                drawShape(canvas, 0f, 0f, 112f, 112f, 112f, "", false, false)
                drawShape(canvas, 0f, -68f, 30f, 30f, 10f, "▲", KeyEvent.KEYCODE_W in pressed, false)
                drawShape(canvas, -68f, 0f, 30f, 30f, 10f, "◀", KeyEvent.KEYCODE_A in pressed, false)
                drawShape(canvas, 68f, 0f, 30f, 30f, 10f, "▶", KeyEvent.KEYCODE_D in pressed, false)
                drawShape(canvas, 0f, 68f, 30f, 30f, 10f, "▼", KeyEvent.KEYCODE_S in pressed, false)
            } else {
                drawShape(canvas, 0f, 0f, if (control.wide) 82f else 39f,
                    if (control.wide) 31f else 39f, if (control.wide) 13f else 39f, control.text,
                    control.key in pressed, control.key == KeyEvent.KEYCODE_F)
            }
            canvas.restore()
        }
        canvas.restore()
    }

    private fun drawShape(canvas: Canvas, x: Float, y: Float, w: Float, h: Float, radius: Float,
        label: String, down: Boolean, accelerator: Boolean) {
        bounds.set(x - w, y - h, x + w, y + h)
        paint.style = Paint.Style.FILL
        paint.color = when {
            down -> PortStyle.accent
            accelerator -> PortStyle.red
            else -> PortStyle.raised
        }
        paint.alpha = ((if (down || editing) 1f else opacity / 100f) * 223f).roundToInt()
        canvas.drawRoundRect(bounds, radius, radius, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = if (down) 4f else 2f
        paint.color = if (down || accelerator) PortStyle.accent else 0xffad798f.toInt()
        paint.alpha = if (editing || down) 255 else (opacity * 2.2f).roundToInt()
        canvas.drawRoundRect(bounds, radius, radius, paint)
        paint.style = Paint.Style.FILL
        paint.color = PortStyle.text
        paint.typeface = typeface
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = if (w > 50) 25f else 39f
        canvas.drawText(label, x, y - (paint.ascent() + paint.descent()) / 2, paint)
    }
}
