package org.pistonport.nativeandroid

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

internal class RaceSettingsPanel(
    context: Context,
    state: State,
    private val hasMako: Boolean,
    private val hasLossless: Boolean,
    private val onGraphics: (State) -> Unit,
    private val onOverlay: (State) -> Unit,
    onEditControls: () -> Unit,
    private val nativeStatus: () -> Int,
    private val upscalerStatus: () -> Int
) : FrameLayout(context) {
    data class State(var framegen: Int, var upscaler: Int, var model: Int,
        var fps: Boolean, var touch: Boolean)

    private lateinit var pane: LinearLayout
    private val status = PortStyle.text(context, "", 13f, PortStyle.muted)
    private val upscaleReadout = PortStyle.text(context, "", 13f, PortStyle.muted)
    private val tick = object : Runnable {
        override fun run() {
            val provider = upscalerStatus()
            upscaleReadout.text = when {
                state.upscaler == 0 -> ""
                provider == 4 && state.upscaler != 4 -> context.getString(R.string.upscale_fallback)
                provider != 0 -> context.getString(R.string.upscaler_active, upscalerName(provider))
                state.upscaler == 2 -> context.getString(R.string.upscale_waiting_taa)
                else -> context.getString(R.string.upscale_waiting)
            }
            upscaleReadout.visibility = if (state.upscaler == 0) View.GONE else View.VISIBLE
            status.text = when {
                state.framegen == 0 -> context.getString(R.string.framegen_off)
                state.framegen == 5 -> context.getString(R.string.blend_detail)
                state.framegen == 1 && state.upscaler != 1 -> context.getString(R.string.fg_fsr_hint)
                state.framegen == 1 -> context.getString(R.string.fg_fsr_active)
                !modelReady(state.model) -> context.getString(R.string.model_required)
                else -> when (nativeStatus()) {
                    -2 -> context.getString(R.string.framegen_unsupported)
                    -1 -> context.getString(R.string.framegen_failed)
                    2 -> context.getString(R.string.framegen_active, state.framegen)
                    else -> context.getString(R.string.framegen_loading)
                }
            }
            postDelayed(this, 500)
        }
    }

    init {
        setBackgroundColor(0x52000000)
        setOnClickListener { visibility = View.GONE }
        pane = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = true
            background = PortStyle.panel(context, PortStyle.surface, 18, PortStyle.border)
            setPadding(context.dp(16), context.dp(12), context.dp(16), context.dp(12))
        }
        addView(pane, LayoutParams(context.dp(326), -1, Gravity.RIGHT).apply {
            setMargins(context.dp(8), context.dp(8), context.dp(8), context.dp(8))
        })
        val header = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(PortStyle.text(context, context.getString(R.string.race_settings), 19f, bold = true),
                LinearLayout.LayoutParams(0, -2, 1f))
            addView(button(context.getString(R.string.close)) { this@RaceSettingsPanel.visibility = View.GONE })
        }
        pane.addView(header)
        val content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        pane.addView(ScrollView(context).apply { addView(content) }, LinearLayout.LayoutParams(-1, 0, 1f))

        toggle(content, context.getString(R.string.show_fps), state.fps) { state.fps = it; onOverlay(state) }
        section(content, context.getString(R.string.controls))
        toggle(content, context.getString(R.string.touch), state.touch) { state.touch = it; onOverlay(state) }
        content.addView(button(context.getString(R.string.edit_controls)) { onEditControls() },
            LinearLayout.LayoutParams(-1, context.dp(44)))
        section(content, "Upscaler")
        options(content, listOf(context.getString(R.string.off), "FSR 3.1", "TAA", "GSR 2", "FSR 1"), 2, { state.upscaler }) {
            state.upscaler = it
            onGraphics(state)
        }
        content.addView(upscaleReadout)
        section(content, context.getString(R.string.frame_generation))
        options(content, listOf(context.getString(R.string.off), "FSR 2×", "Piston Frame 2×", "3×", "4×", context.getString(R.string.blend_short)), 3, { state.framegen }) {
            state.framegen = it
            onGraphics(state)
        }
        section(content, context.getString(R.string.frame_model))
        options(content, listOf("Lossless Scaling", "MAKO-FG"), 2, { state.model }) {
            state.model = it
            onGraphics(state)
        }
        status.setPadding(0, context.dp(10), 0, context.dp(8))
        content.addView(status)
        visibility = View.GONE
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (changedView !== this) return
        removeCallbacks(tick)
        if (visibility == View.VISIBLE) post(tick)
    }

    fun open(fromLeft: Boolean) {
        val params = pane.layoutParams as LayoutParams
        params.gravity = if (fromLeft) Gravity.LEFT else Gravity.RIGHT
        pane.layoutParams = params
        visibility = View.VISIBLE
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(tick)
        super.onDetachedFromWindow()
    }

    fun modelReady(model: Int) = if (model == 1) hasMako else hasLossless

    private fun toggle(parent: LinearLayout, label: String, checked: Boolean, change: (Boolean) -> Unit) {
        parent.addView(Switch(context).apply {
            text = label
            setTextColor(PortStyle.text)
            minHeight = context.dp(42)
            isChecked = checked
            setOnCheckedChangeListener { _, enabled -> change(enabled) }
        })
    }

    private fun section(parent: LinearLayout, title: String) {
        parent.addView(PortStyle.text(context, title, 15f, bold = true).apply {
            setPadding(0, context.dp(12), 0, context.dp(4))
        })
    }

    private fun options(parent: LinearLayout, labels: List<String>, columns: Int,
        selected: () -> Int, change: (Int) -> Unit) {
        val buttons = mutableListOf<Button>()
        fun update() {
            buttons.forEachIndexed { index, control ->
                control.isSelected = selected() == index
                control.background = PortStyle.action(context,
                    if (control.isSelected) PortStyle.red else PortStyle.raised, 10, PortStyle.border)
            }
        }
        for (group in labels.withIndex().chunked(columns)) {
            val row = LinearLayout(context)
            for ((index, label) in group) {
                val control = button(label) { change(index); update() }
                buttons.add(control)
                row.addView(control, LinearLayout.LayoutParams(0, context.dp(44), 1f).apply {
                    setMargins(context.dp(2), context.dp(2), context.dp(2), context.dp(2))
                })
            }
            parent.addView(row)
        }
        update()
    }

    private fun button(label: String, action: () -> Unit) = Button(context).apply {
        text = label
        textSize = 12f
        isAllCaps = false
        setTextColor(Color.WHITE)
        minimumWidth = 0
        minWidth = 0
        setPadding(context.dp(6), 0, context.dp(6), 0)
        stateListAnimator = null
        background = PortStyle.action(context, PortStyle.raised, 10, PortStyle.border)
        setOnClickListener { action() }
    }
}
