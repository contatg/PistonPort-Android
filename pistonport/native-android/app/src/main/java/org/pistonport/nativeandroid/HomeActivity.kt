package org.pistonport.nativeandroid

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.DataInputStream
import java.io.File
import ro.cobrabm.fexdroid.PistonSource

class HomeActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var diagnostics: TextView
    private lateinit var play: Button
    private lateinit var select: Button
    private var selecting = false

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (24 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }
        column.addView(TextView(this).apply {
            text = "PistonPort Android"
            textSize = 28f
        })
        status = TextView(this)
        column.addView(status)
        select = Button(this).apply {
            text = "Selecionar pasta do jogo"
            setOnClickListener {
                if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                    openPicker()
                } else {
                    requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), STORAGE)
                }
            }
        }
        column.addView(select)
        play = Button(this).apply {
            text = "Jogar"
            setOnClickListener {
                val folder = PistonSource.current(filesDir) ?: return@setOnClickListener
                val image = bootImage(folder) ?: return@setOnClickListener
                startActivity(Intent(this@HomeActivity, GameActivity::class.java)
                    .putExtra("folder", folder.path).putExtra("image", image.path))
            }
        }
        column.addView(play)
        column.addView(Button(this).apply {
            text = "Testar Vulkan"
            setOnClickListener { startActivity(Intent(this@HomeActivity, GameActivity::class.java)) }
        })
        diagnostics = TextView(this).apply { textIsSelectable = true }
        column.addView(diagnostics)
        setContentView(ScrollView(this).apply { addView(column) })
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val folder = PistonSource.current(filesDir)
        val prepared = folder?.let(::bootImage)
        select.isEnabled = !selecting
        play.isEnabled = !selecting && prepared != null
        status.text = when {
            selecting -> "Verificando a pasta do jogo…"
            folder == null -> "Selecione a pasta extraída CUSA07027 v01.00 que contém eboot.bin."
            prepared == null -> "Pasta selecionada. Coloque o arquivo cars3-arm64-low.bin preparado pelo PistonPort do PC nesta pasta."
            else -> "Pasta pronta para iniciar."
        }
        diagnostics.text = runCatching {
            GameActivity.logFile(this).inputStream().use { stream ->
                val buffer = ByteArray(4096)
                val count = stream.read(buffer)
                buffer.decodeToString(endIndex = count.coerceAtLeast(0))
            }
        }.getOrDefault("")
        diagnostics.visibility = if (diagnostics.text.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun openPicker() = startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), PICK_FOLDER)

    override fun onRequestPermissionsResult(code: Int, permissions: Array<String>, results: IntArray) {
        super.onRequestPermissionsResult(code, permissions, results)
        if (code != STORAGE) return
        if (results.firstOrNull() == PackageManager.PERMISSION_GRANTED) openPicker()
        else status.text = "Permita a leitura do armazenamento para abrir a pasta original."
    }

    @Deprecated("The app uses the platform folder picker.")
    override fun onActivityResult(code: Int, result: Int, data: Intent?) {
        super.onActivityResult(code, result, data)
        val uri = data?.data ?: return
        if (code != PICK_FOLDER || result != RESULT_OK || selecting) return
        selecting = true
        refresh()
        Thread {
            val selection = runCatching { PistonSource.selectFolder(applicationContext, uri) }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                selecting = false
                refresh()
                selection.exceptionOrNull()?.let { status.text = it.message ?: "Falha ao abrir a pasta." }
            }
        }.start()
    }

    private fun bootImage(folder: File): File? = runCatching {
        val file = File(folder, "cars3-arm64-low.bin")
        val magic = ByteArray(8)
        DataInputStream(file.inputStream()).use { it.readFully(magic) }
        file.takeIf { magic.decodeToString() == "G3PROBE5" }
    }.getOrNull()

    private companion object {
        const val STORAGE = 1
        const val PICK_FOLDER = 2
    }
}
