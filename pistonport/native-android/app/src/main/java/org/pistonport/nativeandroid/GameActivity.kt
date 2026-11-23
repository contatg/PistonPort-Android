package org.pistonport.nativeandroid

import android.content.Context
import android.os.Bundle
import android.os.Process
import android.system.Os
import android.view.Gravity
import android.widget.FrameLayout
import java.io.File
import org.libsdl.app.SDLActivity

class GameActivity : SDLActivity() {
    private var hud: TouchControllerOverlay? = null
    private var performance: PerformanceHud? = null

    override fun getLibraries() = arrayOf("SDL3", "pistonport")

    override fun getArguments(): Array<String> {
        val folder = intent.getStringExtra("folder")
        val image = intent.getStringExtra("image")
        return if (folder != null && image != null) {
            arrayOf(image, "--app0", folder, "--user", profile(this).path, "--timeout", "0")
        } else {
            arrayOf("--vulkan-only")
        }
    }

    override fun onCreate(state: Bundle?) {
        val profile = profile(this)
        profile.mkdirs()
        val driverId = intent.getStringExtra("driver_id")
        val log = if (intent.hasExtra("image")) logFile(this) else vulkanLog(this, driverId)
        Os.setenv("PISTON_LOG_FILE", log.path, true)
        Os.setenv("SPARTAN_CONFIG", File(profile, "pistonport.ini").path, true)
        Os.setenv("SPARTAN_GPU_USER_DIR", profile.path, true)
        Os.setenv("SPARTAN_FPS_LIMIT", intent.getIntExtra("fps", 30).toString(), true)
        Os.setenv("SPARTAN_FULLSCREEN", "1", true)
        if (driverId != null) {
            Os.setenv("PISTON_TURNIP_DIR", TurnipDriver.directory(this, driverId).path + "/", true)
            Os.setenv("PISTON_DRIVER_HOOKS", applicationInfo.nativeLibraryDir + "/", true)
        } else {
            Os.unsetenv("PISTON_TURNIP_DIR")
            Os.unsetenv("PISTON_DRIVER_HOOKS")
        }
        Os.setenv("SPARTAN_ANISOTROPY", intent.getIntExtra("filtering", 0).toString(), true)
        configureGraphics(profile)
        super.onCreate(state)
        if (!mBrokenLibraries && intent.hasExtra("image") && intent.getBooleanExtra("touch", true)) {
            hud = TouchControllerOverlay(this) { buttons, lx, ly, rx, ry, left, right, l2, r2 ->
                val mask = buttons.fold(0) { bits, button -> bits or (touchButtons[button] ?: 0) }
                updateTouch(mask, lx, ly, rx, ry, left, right, l2, r2)
            }.also { controls ->
                controls.alpha = intent.getIntExtra("control_opacity", 85).coerceIn(55, 100) / 100f
                addContentView(controls, FrameLayout.LayoutParams(-1, -1))
            }
        }
        if (!mBrokenLibraries && intent.hasExtra("image") && intent.getBooleanExtra("performance_hud", true)) {
            performance = PerformanceHud(this, when (intent.getIntExtra("upscaler", 0)) {
                1 -> "FSR 3.1"
                2 -> "TAA"
                else -> ""
            }).also {
                addContentView(it, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.RIGHT)
                    .apply { topMargin = dp(12); marginEnd = dp(12) })
            }
        }
    }

    private fun configureGraphics(profile: File) {
        fun number(key: String, default: Int) = intent.getIntExtra(key, default)
        fun flag(key: String, default: Boolean) = if (intent.getBooleanExtra(key, default)) "1" else "0"
        val output = number("output", 1080).coerceIn(540, 1440)
        val aspect = aspectRatios.getOrElse(number("aspect", 0)) { "auto" }
        val factors = aspect.split(':')
        val ratio = if (factors.size == 2) factors[0].toDouble() / factors[1].toDouble() else 16.0 / 9
        val width = ((output * ratio / 2).toInt() * 2).coerceAtMost(3840)
        val upscaler = when (number("upscaler", 0)) {
            1 -> "fsr3"
            2 -> "taa"
            else -> "off"
        }
        val generation = number("framegen", 0).coerceIn(0, 4)
        val video = mapOf(
            "SPARTAN_UPSCALER" to upscaler,
            "SPARTAN_UPSCALE_PRESET" to number("preset", 3).toString(),
            "SPARTAN_OUTPUT_RES" to "${width}x$output",
            "PISTON_ASPECT" to if (aspect == "auto") "0" else ratio.toString(),
            "SPARTAN_FSR_SHARPNESS" to (number("sharpness", 20) / 100f).toString(),
            "PISTON_SHARPEN" to if (number("sharpness", 20) > 0) "1" else "0",
            "PISTON_VSYNC" to flag("vsync", true),
            "PISTON_TEXTURE_QUALITY" to number("texture", 0).toString(),
            "SPARTAN_ASYNC_SHADERS" to flag("async", true),
            "PISTON_BLOOM" to flag("bloom", true),
            "SPARTAN_MOTION_BLUR" to flag("motion_blur", true),
            "PISTON_SHADOWS" to flag("shadows", true),
            "SPARTAN_DEPTH_OF_FIELD" to flag("dof", true),
            "PISTON_FSR_FRAMEGEN" to if (generation == 1) "fsr" else "off",
            "PISTON_FRAMEGEN" to generation.toString(),
            "PISTON_FRAMEGEN_MODEL" to if (number("fg_model", 0) == 1) "mako" else "lsfg",
            "PISTON_FRAMEGEN_FLOW" to number("fg_flow", 75).toString(),
            "PISTON_FRAMEGEN_PERFORMANCE" to flag("fg_fast", true),
            "PISTON_FRAMEGEN_FP16" to flag("fg_fp16", true))
        for ((key, value) in video) Os.setenv(key, value, true)
        val dll = FrameGenAssets.lossless(profile)
        val mako = FrameGenAssets.mako(profile)
        if (dll.isFile) Os.setenv("PISTON_LOSSLESS_DLL", dll.path, true)
        if (FrameGenAssets.makoInstalled(profile)) Os.setenv("PISTON_MAKO_MODEL", mako.path, true)
    }

    override fun onResume() {
        super.onResume()
        performance?.start()
    }

    override fun onPause() {
        performance?.stop()
        hud?.release()
        super.onPause()
    }

    override fun onDestroy() {
        performance?.close()
        super.onDestroy()
        if (isFinishing) Process.killProcess(Process.myPid())
    }

    private external fun updateTouch(buttons: Int, lx: Int, ly: Int, rx: Int, ry: Int,
        left: Boolean, right: Boolean, l2: Int, r2: Int)

    companion object {
        // libScePad button bits and the runtime's host-only right touchpad click.
        private val touchButtons = mapOf(
            "l3" to 0x2, "r3" to 0x4, "options" to 0x8,
            "up" to 0x10, "right" to 0x20, "down" to 0x40, "left" to 0x80,
            "l1" to 0x400, "r1" to 0x800, "triangle" to 0x1000,
            "circle" to 0x2000, "cross" to 0x4000, "square" to 0x8000,
            "touchpad" to Int.MIN_VALUE)

        private fun profile(context: Context) = File(context.filesDir, "pistonport")
        fun logFile(context: Context) = File(profile(context), "native-last.log")
        fun vulkanLog(context: Context, id: String?) = File(profile(context), "vulkan-${id ?: "system"}.log")
    }
}
