package org.pistonport.nativeandroid

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.View
import android.widget.TextView
import kotlin.math.roundToInt

internal fun Context.dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()

internal object PortStyle {
    val background = Color.rgb(16, 14, 17)
    val surface = Color.rgb(28, 24, 30)
    val raised = Color.rgb(42, 32, 42)
    val border = Color.rgb(65, 51, 63)
    val text = Color.rgb(251, 244, 247)
    val muted = Color.rgb(197, 178, 190)
    val accent = Color.rgb(255, 81, 100)
    val red = Color.rgb(194, 31, 55)

    fun panel(context: Context, color: Int, radius: Int, stroke: Int = Color.TRANSPARENT) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = context.dp(radius).toFloat()
            setStroke(context.dp(1), stroke)
        }

    fun action(context: Context, color: Int, radius: Int, stroke: Int = Color.TRANSPARENT) =
        RippleDrawable(ColorStateList.valueOf(0x33ffffff), panel(context, color, radius, stroke), null)

    fun text(context: Context, value: String, size: Float, color: Int = text, bold: Boolean = false) =
        TextView(context).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
}

internal class RaceBanner(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val polygon = Path()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        paint.shader = if (w > 0 && h > 0) LinearGradient(0f, h.toFloat(), w.toFloat(), 0f,
            intArrayOf(0xff35151f.toInt(), 0xff781d32.toInt(), 0xffb62843.toInt()),
            null, Shader.TileMode.CLAMP) else null
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val shader = paint.shader
        paint.alpha = 255
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null
        paint.color = 0x24ffb0bd
        for (i in 0..4) {
            val left = w * (0.57f + i * .09f)
            polygon.reset()
            polygon.moveTo(left + h * .60f, 0f)
            polygon.lineTo(left + h * .60f + w * .045f, 0f)
            polygon.lineTo(left + w * .045f, h)
            polygon.lineTo(left, h)
            polygon.close()
            canvas.drawPath(polygon, paint)
        }
        paint.color = 0x28ffffff
        val tile = h * .15f
        for (row in 0..2) for (col in 0..3) {
            if ((row + col) % 2 != 0) continue
            val x = w - tile * (4.3f - col) - row * tile * .45f
            val y = h - tile * (3 - row)
            canvas.drawRect(x, y, x + tile, y + tile, paint)
        }
        paint.shader = shader
    }
}
