package org.pistonport.nativeandroid

import android.content.Context
import android.content.Intent

internal class PortSettings(context: Context) {
    private val values = context.getSharedPreferences("port-ui", Context.MODE_PRIVATE)
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

    fun applyTo(intent: Intent): Intent = intent.putExtra("fps", fps).putExtra("filtering", filtering)
        .putExtra("performance_hud", performanceHud).putExtra("touch", touch)
        .putExtra("control_opacity", opacity)
        .putExtra("upscaler", choice("upscaler", 0))
        .putExtra("preset", choice("preset", 3))
        .putExtra("output", choice("output", 1080))
        .putExtra("aspect", choice("aspect", 0))
        .putExtra("sharpness", choice("sharpness", 20))
        .putExtra("texture", choice("texture", 0))
        .putExtra("vsync", toggle("vsync", true))
        .putExtra("bloom", toggle("bloom", true))
        .putExtra("motion_blur", toggle("motion_blur", true))
        .putExtra("shadows", toggle("shadows", true))
        .putExtra("dof", toggle("dof", true))
        .putExtra("async", toggle("async", true))
        .putExtra("framegen", choice("framegen", 0))
        .putExtra("fg_model", choice("fg_model", 0))
        .putExtra("fg_flow", choice("fg_flow", 75))
        .putExtra("fg_fast", toggle("fg_fast", true))
        .putExtra("fg_fp16", toggle("fg_fp16", true))
}

internal val aspectRatios = listOf("auto", "4:3", "16:10", "16:9", "21:9", "32:9")
