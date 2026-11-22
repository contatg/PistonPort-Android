package org.pistonport.nativeandroid

import android.content.Context
import android.os.Bundle
import android.os.Process
import android.system.Os
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import java.io.File
import org.libsdl.app.SDLActivity

class GameActivity : SDLActivity() {
    private val pressed = mutableSetOf<Int>()

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
        Os.setenv("PISTON_LOG_FILE", logFile(this).path, true)
        Os.setenv("SPARTAN_CONFIG", File(profile, "pistonport.ini").path, true)
        Os.setenv("SPARTAN_GPU_USER_DIR", profile.path, true)
        Os.setenv("SPARTAN_FPS_LIMIT", "30", true)
        Os.setenv("SPARTAN_FULLSCREEN", "1", true)
        Os.setenv("SPARTAN_ANISOTROPY", "0", true)
        super.onCreate(state)
        if (!mBrokenLibraries && intent.hasExtra("image")) {
            controls(Gravity.BOTTOM or Gravity.START, listOf(
                "◀" to KeyEvent.KEYCODE_A, "▶" to KeyEvent.KEYCODE_D,
                "▲" to KeyEvent.KEYCODE_W, "▼" to KeyEvent.KEYCODE_S,
            ))
            controls(Gravity.BOTTOM or Gravity.END, listOf(
                "Freio" to KeyEvent.KEYCODE_R, "Acelerar" to KeyEvent.KEYCODE_F,
                "Turbo" to KeyEvent.KEYCODE_E,
            ))
            controls(Gravity.END or Gravity.CENTER_VERTICAL, listOf(
                "Pular" to KeyEvent.KEYCODE_SPACE, "Derrapar" to KeyEvent.KEYCODE_SHIFT_LEFT,
                "Item" to KeyEvent.KEYCODE_Q, "Pausa" to KeyEvent.KEYCODE_ENTER,
            ))
        }
    }

    private fun controls(gravity: Int, keys: List<Pair<String, Int>>) {
        val row = LinearLayout(this).apply { isMotionEventSplittingEnabled = true }
        keys.forEach { (label, key) ->
            row.addView(Button(this).apply {
                text = label
                alpha = 0.76f
                minimumWidth = 0
                minWidth = (48 * resources.displayMetrics.density).toInt()
                setPadding(8, 0, 8, 0)
                isFocusable = false
                setOnClickListener {
                    SDLActivity.onNativeKeyDown(key)
                    postDelayed({ SDLActivity.onNativeKeyUp(key) }, 90)
                }
                setOnTouchListener { _, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> if (pressed.add(key)) SDLActivity.onNativeKeyDown(key)
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                            if (pressed.remove(key)) SDLActivity.onNativeKeyUp(key)
                    }
                    true
                }
            })
        }
        addContentView(row, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, gravity
        ))
    }

    override fun onPause() {
        pressed.forEach { SDLActivity.onNativeKeyUp(it) }
        pressed.clear()
        super.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) Process.killProcess(Process.myPid())
    }

    companion object {
        private fun profile(context: Context) = File(context.filesDir, "pistonport")
        fun logFile(context: Context) = File(profile(context), "native-last.log")
    }
}
