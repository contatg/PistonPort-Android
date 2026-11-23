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
    var size: Int
        get() = values.getInt("size", 100)
        set(value) { values.edit().putInt("size", value).apply() }
    var opacity: Int
        get() = values.getInt("opacity", 85)
        set(value) { values.edit().putInt("opacity", value).apply() }

    fun applyTo(intent: Intent): Intent = intent.putExtra("fps", fps).putExtra("filtering", filtering)
        .putExtra("performance_hud", performanceHud).putExtra("touch", touch).putExtra("control_size", size).putExtra("control_opacity", opacity)
}
