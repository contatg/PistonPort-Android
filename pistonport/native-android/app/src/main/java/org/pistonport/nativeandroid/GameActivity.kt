package org.pistonport.nativeandroid

import android.content.Context
import android.os.Bundle
import android.os.Process
import android.system.Os
import android.widget.FrameLayout
import java.io.File
import org.libsdl.app.SDLActivity

class GameActivity : SDLActivity() {
    private var hud: RaceControls? = null

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
        super.onCreate(state)
        if (!mBrokenLibraries && intent.hasExtra("image") && intent.getBooleanExtra("touch", true)) {
            hud = RaceControls(this, intent.getIntExtra("control_size", 100),
                intent.getIntExtra("control_opacity", 85), { key, down ->
                    if (down) SDLActivity.onNativeKeyDown(key) else SDLActivity.onNativeKeyUp(key)
                }).also { controls ->
                addContentView(controls, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
                ))
            }
        }
    }

    override fun onPause() {
        hud?.release()
        super.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) Process.killProcess(Process.myPid())
    }

    companion object {
        private fun profile(context: Context) = File(context.filesDir, "pistonport")
        fun logFile(context: Context) = File(profile(context), "native-last.log")
        fun vulkanLog(context: Context, id: String?) = File(profile(context), "vulkan-${id ?: "system"}.log")
    }
}
