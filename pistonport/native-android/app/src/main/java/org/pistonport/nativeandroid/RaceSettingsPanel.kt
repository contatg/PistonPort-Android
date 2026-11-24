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
                provider == 4 && state.upscaler != 4 -> "FSR 1 ativo como alternativa."
                provider != 0 -> "${upscalerName(provider)} ativo."
                state.upscaler == 2 -> "Aguardando uma cena compatível com TAA."
                else -> "Aguardando um quadro abaixo da resolução de saída."
            }
            upscaleReadout.visibility = if (state.upscaler == 0) View.GONE else View.VISIBLE
            status.text = when {
                state.framegen == 0 -> "Framegen desligado."
                state.framegen == 5 -> "Mescla dois quadros do jogo. Veja o resultado no medidor de FPS."
                state.framegen == 1 && state.upscaler != 1 -> "Selecione o upscaler FSR 3.1 para usar este framegen."
                state.framegen == 1 -> "FSR 3.1 selecionado. O medidor indica quando há quadros gerados."
                !modelReady(state.model) -> "Instale o modelo ou importe sua Lossless.dll no launcher."
                else -> when (nativeStatus()) {
                    -2 -> "O driver atual não oferece suporte ao Bloodframe."
                    -1 -> "Bloodframe falhou. Consulte o log do jogo."
                    2 -> "Bloodframe ativo • ${state.framegen}×"
                    else -> "Preparando os quadros do Bloodframe…"
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
            addView(PortStyle.text(context, "Ajustes de corrida", 19f, bold = true),
                LinearLayout.LayoutParams(0, -2, 1f))
            addView(button("Fechar") { this@RaceSettingsPanel.visibility = View.GONE })
        }
        pane.addView(header)
        val content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        pane.addView(ScrollView(context).apply { addView(content) }, LinearLayout.LayoutParams(-1, 0, 1f))

        toggle(content, "Mostrar FPS e GPU", state.fps) { state.fps = it; onOverlay(state) }
        section(content, "Controles")
        toggle(content, "Controles por toque", state.touch) { state.touch = it; onOverlay(state) }
        content.addView(button("Editar controles") { onEditControls() },
            LinearLayout.LayoutParams(-1, context.dp(44)))
        section(content, "Upscaler")
        options(content, listOf("Desligado", "FSR 3.1", "TAA", "GSR 2", "FSR 1"), 2, { state.upscaler }) {
            state.upscaler = it
            onGraphics(state)
        }
        content.addView(upscaleReadout)
        section(content, "Geração de quadros")
        options(content, listOf("Desligado", "FSR 2×", "Bloodframe 2×", "3×", "4×", "Mescla 2×"), 3, { state.framegen }) {
            state.framegen = it
            onGraphics(state)
        }
        section(content, "Modelo Bloodframe")
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
