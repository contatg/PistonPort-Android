package org.pistonport.nativeandroid

import android.content.Context
import android.graphics.Rect
import android.os.Bundle
import android.os.Process
import android.system.Os
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.FrameLayout
import java.io.File
import org.libsdl.app.SDLActivity

class GameActivity : SDLActivity() {
    private var hud: TouchControllerOverlay? = null
    private var performance: PerformanceHud? = null
    private var racePanel: RaceSettingsPanel? = null
    private var resumed = false
    private var edgeStartX = Float.NaN
    private var edgeStartY = 0f
    private var edgeDirection = 0
    private var openingPanel = false
    private lateinit var raceSettings: RaceSettingsPanel.State

    override fun getLibraries() = arrayOf("SDL3", "pistonport")

    override fun getArguments(): Array<String> {
        val folder = requireNotNull(intent.getStringExtra("folder"))
        val image = requireNotNull(intent.getStringExtra("image"))
        return arrayOf(image, "--app0", folder, "--user", profile(this).path, "--timeout", "0")
    }

    // SDL may request portrait or a resizable window after native startup.
    // Keep the game in landscape, independently of the device rotation lock.
    override fun setOrientationBis(w: Int, h: Int, resizable: Boolean, hint: String?) {
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    override fun onCreate(state: Bundle?) {
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        val profile = profile(this)
        profile.mkdirs()
        val driverId = intent.getStringExtra("driver_id")
        val driverKind = DriverKind.entries.firstOrNull { it.name == intent.getStringExtra("driver_kind") }
        Os.setenv("PISTON_LOG_FILE", logFile(this).path, true)
        Os.setenv("SPARTAN_CONFIG", File(profile, "pistonport.ini").path, true)
        Os.setenv("SPARTAN_GPU_USER_DIR", profile.path, true)
        Os.setenv("SPARTAN_FPS_LIMIT", intent.getIntExtra("fps", 30).toString(), true)
        Os.setenv("SPARTAN_FULLSCREEN", "1", true)
        Os.unsetenv("PISTON_TURNIP_DIR")
        Os.unsetenv("PISTON_PANVK_DIR")
        Os.unsetenv("PISTON_DRIVER_HOOKS")
        if (driverId != null && driverKind != null) {
            val directory = VulkanDriver.directory(this, driverKind, driverId)
            val variable = if (driverKind == DriverKind.PANVK) "PISTON_PANVK_DIR" else "PISTON_TURNIP_DIR"
            Os.setenv(variable, directory.path + "/", true)
            Os.setenv("PISTON_DRIVER_HOOKS", applicationInfo.nativeLibraryDir + "/", true)
        }
        Os.setenv("SPARTAN_ANISOTROPY", intent.getIntExtra("filtering", 0).toString(), true)
        configureGraphics(profile)
        super.onCreate(state)
        if (mBrokenLibraries) return
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enterImmersiveMode()
        raceSettings = RaceSettingsPanel.State(
            state?.getInt("race_framegen") ?: intent.getIntExtra("framegen", 0),
            state?.getInt("race_upscaler") ?: intent.getIntExtra("upscaler", 0),
            state?.getInt("race_model") ?: intent.getIntExtra("fg_model", 0),
            state?.getBoolean("race_fps") ?: intent.getBooleanExtra("performance_hud", true),
            state?.getBoolean("race_touch") ?: intent.getBooleanExtra("touch", true))
        hud = TouchControllerOverlay(this) { buttons, lx, ly, rx, ry, left, right, l2, r2 ->
            val mask = buttons.fold(0) { bits, button -> bits or (touchButtons[button] ?: 0) }
            updateTouch(mask, lx, ly, rx, ry, left, right, l2, r2)
        }.also { controls ->
            controls.alpha = intent.getIntExtra("control_opacity", 85).coerceIn(55, 100) / 100f
            addContentView(controls, FrameLayout.LayoutParams(-1, -1))
        }
        performance = PerformanceHud(this, ::upscalerStatus).also {
            addContentView(it, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.RIGHT)
                .apply { topMargin = dp(12); marginEnd = dp(12) })
        }
        val hasMako = FrameGenAssets.makoInstalled(profile)
        val hasLossless = FrameGenAssets.lossless(profile).isFile
        val panel = RaceSettingsPanel(this, raceSettings, hasMako, hasLossless,
            onGraphics = ::applyRaceGraphics, onOverlay = { updateRaceOverlays() },
            onEditControls = ::editRaceControls,
            nativeStatus = ::pitframeStatus, upscalerStatus = ::upscalerStatus)
        racePanel = panel
        addContentView(panel, FrameLayout.LayoutParams(-1, -1))
        val decor = window.decorView
        decor.addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
            val center = view.height / 2
            val halfHeight = minOf(center, dp(90))
            view.systemGestureExclusionRects = listOf(
                Rect(0, center - halfHeight, minOf(view.width, dp(24)), center + halfHeight),
                Rect((view.width - dp(24)).coerceAtLeast(0), center - halfHeight, view.width, center + halfHeight)
            )
        }
        updateRaceOverlays()
        applyRaceGraphics(raceSettings)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (hud?.isEditing() == true) return super.dispatchTouchEvent(event)
        val panel = racePanel ?: return super.dispatchTouchEvent(event)
        val action = event.actionMasked
        if (action == MotionEvent.ACTION_DOWN) {
            edgeDirection = when {
                event.x <= dp(24) -> 1
                event.x >= window.decorView.width - dp(24) -> -1
                else -> 0
            }
            edgeStartX = if (edgeDirection != 0 && panel.visibility != View.VISIBLE) event.x else Float.NaN
            edgeStartY = event.y
            openingPanel = false
        }
        if (openingPanel) {
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) openingPanel = false
            return true
        }
        if (action == MotionEvent.ACTION_POINTER_DOWN || action == MotionEvent.ACTION_CANCEL ||
            action == MotionEvent.ACTION_UP) {
            edgeStartX = Float.NaN
        }
        if (action == MotionEvent.ACTION_MOVE && !edgeStartX.isNaN()) {
            val horizontal = (event.x - edgeStartX) * edgeDirection
            val vertical = kotlin.math.abs(edgeStartY - event.y)
            if (vertical > dp(32) && vertical > horizontal) edgeStartX = Float.NaN
            if (horizontal >= dp(42) && horizontal >= vertical * 1.5f) {
                val cancel = MotionEvent.obtain(event)
                cancel.action = MotionEvent.ACTION_CANCEL
                super.dispatchTouchEvent(cancel)
                cancel.recycle()
                hud?.release()
                panel.open(fromLeft = edgeDirection == 1)
                openingPanel = true
                edgeStartX = Float.NaN
                return true
            }
        }
        return super.dispatchTouchEvent(event)
    }

    private fun applyRaceGraphics(state: RaceSettingsPanel.State) {
        val available = when (state.framegen) {
            0, 5 -> true
            1 -> state.upscaler == 1
            else -> racePanel?.modelReady(state.model) == true
        }
        setLiveGraphics(if (available) state.framegen else 0, state.upscaler, state.model)

    }

    private fun updateRaceOverlays() {
        hud?.let { controls ->
            val visible = raceSettings.touch || controls.isEditing()
            controls.visibility = if (visible) View.VISIBLE else View.GONE
            if (!visible) controls.release()
        }
        performance?.visibility = if (raceSettings.fps) View.VISIBLE else View.GONE
        if (raceSettings.fps && resumed) performance?.start() else performance?.stop()
    }

    private fun editRaceControls() {
        val controls = hud ?: return
        racePanel?.visibility = View.GONE
        edgeStartX = Float.NaN
        openingPanel = false
        controls.visibility = View.VISIBLE
        controls.edit { updateRaceOverlays() }
    }

    override fun onSaveInstanceState(state: Bundle) {
        if (::raceSettings.isInitialized) {
            state.putInt("race_framegen", raceSettings.framegen)
            state.putInt("race_upscaler", raceSettings.upscaler)
            state.putInt("race_model", raceSettings.model)
            state.putBoolean("race_fps", raceSettings.fps)
            state.putBoolean("race_touch", raceSettings.touch)
        }
        super.onSaveInstanceState(state)
    }

    @Deprecated("The game uses legacy Back dispatch to close its overlays.")
    override fun onBackPressed() {
        if (mBrokenLibraries) {
            finish()
            return
        }
        val controls = hud
        when {
            controls?.isEditing() == true -> controls.finishEdit()
            racePanel?.visibility == View.VISIBLE -> racePanel?.visibility = View.GONE
        }
        enterImmersiveMode()
    }

    private fun enterImmersiveMode() {
        window.insetsController?.apply {
            systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsets.Type.systemBars())
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !mBrokenLibraries) enterImmersiveMode()
    }

    private external fun setLiveGraphics(framegen: Int, upscaler: Int, model: Int)
    private external fun pitframeStatus(): Int
    private external fun upscalerStatus(): Int

    private fun configureGraphics(profile: File) {
        fun number(key: String, default: Int) = intent.getIntExtra(key, default)
        fun flag(key: String, default: Boolean) = if (intent.getBooleanExtra(key, default)) "1" else "0"
        val output = number("output", 1080).coerceIn(540, 1440)
        val aspect = aspectRatios.getOrElse(number("aspect", 0)) { "auto" }
        val factors = aspect.split(':')
        val ratio = if (factors.size == 2) factors[0].toDouble() / factors[1].toDouble() else 16.0 / 9
        // Android's selectable guest resolutions have a 16:9 picture.
        val width = (output * 16 / 9) and -2
        val upscaler = when (number("upscaler", 0)) {
            1 -> "fsr3"
            2 -> "taa"
            3 -> "gsr2"
            else -> "off"
        }
        val generation = number("framegen", 0).coerceIn(0, 5)
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
        if (intent.getBooleanExtra("lite_mode", false)) {
            Os.setenv("PISTON_PRESENT_HEIGHT", "360", true)
            Os.setenv("SPARTAN_FRAMES_AHEAD", "2", true)
            Os.setenv("SPARTAN_RCAS", "0", true)
        }
        val dll = FrameGenAssets.lossless(profile)
        val mako = FrameGenAssets.mako(profile)
        if (dll.isFile) Os.setenv("PISTON_LOSSLESS_DLL", dll.path, true)
        if (FrameGenAssets.makoInstalled(profile)) Os.setenv("PISTON_MAKO_MODEL", mako.path, true)
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        if (::raceSettings.isInitialized) updateRaceOverlays()
    }

    override fun onPause() {
        resumed = false
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
    }
}
