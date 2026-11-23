package org.pistonport.nativeandroid

import android.app.Activity
import android.os.Bundle
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.FrameLayout

class TouchLayoutActivity : Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val root = FrameLayout(this)
        root.addView(RaceBanner(this).apply { alpha = .35f }, FrameLayout.LayoutParams(-1, -1))
        val editor = TouchControllerOverlay(this) { _, _, _, _, _, _, _, _, _ -> }
        root.addView(editor, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        window.insetsController?.apply {
            systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsets.Type.systemBars())
        }
        editor.edit { finish() }
    }
}
