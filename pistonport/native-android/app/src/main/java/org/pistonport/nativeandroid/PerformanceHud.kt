package org.pistonport.nativeandroid

import android.content.Context
import android.os.SystemClock
import android.view.View
import android.widget.FrameLayout
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

internal class PerformanceHud(context: Context) : FrameLayout(context) {
    private val readout = PortStyle.text(context, "FPS —    GPU —", 12f, bold = true)
    private val worker = Executors.newSingleThreadScheduledExecutor()
    private val sensors = AdrenoLoad()
    private var samples: ScheduledFuture<*>? = null
    private var generation = 0

    init {
        background = PortStyle.panel(context, 0xe61c181e.toInt(), 12, PortStyle.accent)
        setPadding(context.dp(12), context.dp(6), context.dp(12), context.dp(6))
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        addView(readout)
    }

    fun start() {
        if (samples != null) return
        val active = ++generation
        var lastCount = presentedFrames()
        var lastTime = SystemClock.elapsedRealtime()
        samples = worker.scheduleWithFixedDelay({
            val now = SystemClock.elapsedRealtime()
            val count = presentedFrames()
            val fps = if (now > lastTime && count > 0)
                (count - lastCount) * 1000.0 / (now - lastTime) else null
            lastCount = count
            lastTime = now
            val gpu = sensors.sample()
            val rate = fps?.let { String.format(Locale.getDefault(), "%.1f", it) } ?: "—"
            val frameTime = fps?.takeIf { it > 0 }?.let {
                String.format(Locale.getDefault(), "    %.1f ms", 1000 / it)
            }.orEmpty()
            val gpuLabel = gpu?.let { "$it%" } ?: "—"
            post {
                if (active == generation) readout.text = "FPS $rate$frameTime    GPU $gpuLabel"
            }
        }, 1, 1, TimeUnit.SECONDS)
    }

    fun stop() {
        generation++
        samples?.cancel(false)
        samples = null
    }

    fun close() {
        stop()
        worker.shutdownNow()
    }

    private external fun presentedFrames(): Long
}

// Adreno sensor selection follows Fractal's HudSensors (dfdx047/Fractal, MPL-2.0).
// Report missing values when Android blocks access to the KGSL counters.
private class AdrenoLoad {
    private val paths = listOf("gpu_busy_percentage", "devfreq/gpu_load", "gpubusy")
        .map { File("/sys/class/kgsl/kgsl-3d0", it) }
    private var selected: File? = null
    private var attempts = 0

    fun sample(): Int? {
        selected?.let { file -> read(file)?.let { return it } }
        if (attempts >= 5) return null
        for (file in paths) {
            val value = read(file) ?: continue
            selected = file
            return value
        }
        attempts++
        return null
    }

    private fun read(file: File): Int? {
        val line = runCatching { file.bufferedReader().use { it.readLine() } }.getOrNull()?.trim()
            ?: return null
        if (file.name != "gpubusy") {
            return line.removeSuffix("%").trim().toIntOrNull()?.takeIf { it in 0..100 }
        }
        val fields = line.split(Regex("\\s+"))
        val busy = fields.getOrNull(0)?.toLongOrNull() ?: return null
        val total = fields.getOrNull(1)?.toLongOrNull() ?: return null
        if (busy !in 0..total) return null
        return if (total == 0L) 0 else (busy * 100.0 / total).roundToInt()
    }
}
