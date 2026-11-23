package org.pistonport.nativeandroid

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.Button
import android.widget.FrameLayout
import kotlin.math.roundToInt

internal class RaceControls(
    context: Context,
    private val scale: Int,
    private val opacity: Int,
    private val onKey: (Int, Boolean) -> Unit,
    private val preview: Boolean = false,
) : FrameLayout(context) {
    private enum class Group { STEERING, ACTION, PEDAL, PAUSE }
    private data class Control(val label: String, val caption: String, val key: Int,
        val group: Group, val column: Int = 0, val row: Int = 0)

    private val controls = listOf(
        Control("▲", "Cima", KeyEvent.KEYCODE_W, Group.STEERING, 1, 0),
        Control("◀", "Esquerda", KeyEvent.KEYCODE_A, Group.STEERING, 0, 1),
        Control("▶", "Direita", KeyEvent.KEYCODE_D, Group.STEERING, 2, 1),
        Control("▼", "Baixo", KeyEvent.KEYCODE_S, Group.STEERING, 1, 2),
        Control("△\nItem", "Item", KeyEvent.KEYCODE_Q, Group.ACTION, 0, 0),
        Control("◇\nPular", "Pular", KeyEvent.KEYCODE_SPACE, Group.ACTION, 1, 0),
        Control("↻\nDrift", "Derrapar", KeyEvent.KEYCODE_SHIFT_LEFT, Group.ACTION, 2, 0),
        Control("»\nTurbo", "Turbo", KeyEvent.KEYCODE_E, Group.ACTION, 3, 0),
        Control("Freio", "Freio", KeyEvent.KEYCODE_R, Group.PEDAL),
        Control("Acelerar", "Acelerar", KeyEvent.KEYCODE_F, Group.PEDAL, 1),
        Control("Ⅱ", "Pausa", KeyEvent.KEYCODE_ENTER, Group.PAUSE),
    )
    private val held = mutableSetOf<Int>()

    init {
        isMotionEventSplittingEnabled = true
        clipChildren = false
        controls.forEach { control ->
            addView(Button(context).apply {
                text = control.label
                contentDescription = control.caption
                isAllCaps = false
                isFocusable = true
                includeFontPadding = false
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, 0)
                minimumWidth = 0
                minimumHeight = 0
                minWidth = 0
                minHeight = 0
                elevation = 0f
                stateListAnimator = null
                setTextColor(PortStyle.text)
                setOnClickListener {
                    setHeld(control, this, true)
                    postDelayed({ setHeld(control, this, false) }, 100)
                }
                setOnTouchListener { _, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> setHeld(control, this, true)
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> setHeld(control, this, false)
                    }
                    true
                }
                style(control, this, false)
            }, LayoutParams(0, 0))
        }
    }

    private fun setHeld(control: Control, button: Button, value: Boolean) {
        val changed = if (value) held.add(control.key) else held.remove(control.key)
        if (changed) {
            onKey(control.key, value)
            style(control, button, value)
        }
    }

    private fun style(control: Control, button: Button, active: Boolean) {
        val red = control.group == Group.PEDAL && control.column == 1
        val fill = when {
            active -> PortStyle.accent
            red -> PortStyle.red
            else -> 0xff231b26.toInt()
        }
        val edge = if (active || red) 0xffff8795.toInt() else 0xff9c6679.toInt()
        val radius = when (control.group) {
            Group.ACTION -> 40
            Group.STEERING, Group.PEDAL -> 13
            Group.PAUSE -> 12
        }
        button.background = PortStyle.panel(context, fill, radius, edge)
        button.alpha = if (active) 1f else opacity / 100f
        button.setTextColor(if (active) Color.rgb(38, 8, 15) else PortStyle.text)
        button.scaleX = if (active) .93f else 1f
        button.scaleY = button.scaleX
    }

    fun release() {
        controls.forEachIndexed { index, control -> setHeld(control, getChildAt(index) as Button, false) }
    }

    override fun onDetachedFromWindow() {
        release()
        super.onDetachedFromWindow()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(width, height)
        val gap = context.dp(if (preview) 3 else 6)
        val edge = context.dp(if (preview) 8 else 16)
        val unit = (context.dp(if (preview) 36 else 53) * scale / 100f).roundToInt()
            .coerceAtMost((width - edge * 2 - gap * 8) / 8)
            .coerceAtMost((height - edge * 2 - gap * 4) / 4).coerceAtLeast(1)
        controls.forEachIndexed { index, control ->
            val button = getChildAt(index) as Button
            val buttonWidth = if (control.group == Group.PEDAL) unit * 2 + gap else unit
            button.textSize = when (control.group) {
                Group.STEERING, Group.PAUSE -> if (preview) 13f else 21f
                else -> if (preview) 9f else 12f
            }
            button.measure(MeasureSpec.makeMeasureSpec(buttonWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(unit, MeasureSpec.EXACTLY))
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val gap = context.dp(if (preview) 3 else 6)
        val edge = context.dp(if (preview) 8 else 16)
        val unit = getChildAt(0).measuredHeight
        val step = unit + gap
        val bottom = height - edge - unit
        val right = width - edge
        controls.forEachIndexed { index, control ->
            val button = getChildAt(index)
            val (x, y) = when (control.group) {
                Group.STEERING -> edge + control.column * step to bottom - (2 - control.row) * step
                Group.ACTION -> right - unit - (3 - control.column) * step to bottom - step
                Group.PEDAL -> right - button.measuredWidth - (1 - control.column) * (button.measuredWidth + gap) to bottom
                Group.PAUSE -> right - unit to edge
            }
            button.layout(x, y, x + button.measuredWidth, y + button.measuredHeight)
        }
    }
}
