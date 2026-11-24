package org.pistonport.nativeandroid

import android.content.Context
import android.content.Intent
import android.os.Build

internal class PortSettings(context: Context) {
    private val values = context.getSharedPreferences("port-ui", Context.MODE_PRIVATE)
    var hardwareNoticeAcknowledged: Boolean
        get() = values.getBoolean("driver_compatibility_acknowledged", false)
        set(value) { values.edit().putBoolean("driver_compatibility_acknowledged", value).apply() }

    var liteMode: Boolean
        get() = values.getBoolean("lite_mode", Build.SOC_MODEL.contains("SM6225", ignoreCase = true) ||
            Build.SOC_MODEL.contains("Snapdragon 685", ignoreCase = true))
        set(value) { values.edit().putBoolean("lite_mode", value).apply() }

    val launchResolution get() = if (liteMode) 360 else resolution
    val launchFrameGeneration get() = if (liteMode) 0 else choice("framegen", 0)

    var resolution: Int
        get() = values.getInt("resolution", 540)
        set(value) { values.edit().putInt("resolution", value).apply() }
    var fps: Int
        get() = values.getInt("fps", 30)
        set(value) { values.edit().putInt("fps", value).apply() }
    var filtering: Int
        get() = values.getInt("filtering", 0)
        set(value) { values.edit().putInt("filtering", value).apply() }
    var performanceHud: Boolean
        get() = values.getBoolean("performance_hud", true)
        set(value) { values.edit().putBoolean("performance_hud", value).apply() }
    var touch: Boolean
        get() = values.getBoolean("touch", true)
        set(value) { values.edit().putBoolean("touch", value).apply() }
    var opacity: Int
        get() = values.getInt("opacity", 85)
        set(value) { values.edit().putInt("opacity", value).apply() }

    fun choice(key: String, default: Int) = values.getInt(key, default)
    fun setChoice(key: String, value: Int) { values.edit().putInt(key, value).apply() }
    fun toggle(key: String, default: Boolean) = values.getBoolean(key, default)
    fun setToggle(key: String, value: Boolean) { values.edit().putBoolean(key, value).apply() }

    fun applyTo(intent: Intent): Intent {
        val lite = liteMode
        return intent.putExtra("lite_mode", lite)
            .putExtra("fps", if (lite) 30 else fps)
            .putExtra("filtering", if (lite) 0 else filtering)
            .putExtra("performance_hud", performanceHud).putExtra("touch", touch)
            .putExtra("control_opacity", opacity)
            .putExtra("upscaler", if (lite) 0 else choice("upscaler", 0))
            .putExtra("preset", choice("preset", 3))
            .putExtra("output", choice("output", 1080))
            .putExtra("aspect", choice("aspect", 0))
            .putExtra("sharpness", if (lite) 0 else choice("sharpness", 20))
            .putExtra("texture", if (lite) 3 else choice("texture", 0))
            .putExtra("vsync", toggle("vsync", true))
            .putExtra("bloom", !lite && toggle("bloom", true))
            .putExtra("motion_blur", !lite && toggle("motion_blur", true))
            .putExtra("shadows", !lite && toggle("shadows", true))
            .putExtra("dof", !lite && toggle("dof", true))
            .putExtra("async", toggle("async", true))
            .putExtra("framegen", launchFrameGeneration)
            .putExtra("fg_model", choice("fg_model", 0))
            .putExtra("fg_flow", choice("fg_flow", 75))
            .putExtra("fg_fast", toggle("fg_fast", true))
            .putExtra("fg_fp16", toggle("fg_fp16", true))
    }
}

internal val aspectRatios = listOf("auto", "4:3", "16:10", "16:9", "21:9", "32:9")
