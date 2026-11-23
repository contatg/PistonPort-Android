package org.pistonport.nativeandroid

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Switch
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import java.io.File
import java.io.RandomAccessFile
import ro.cobrabm.fexdroid.PistonSource

class HomeActivity : Activity() {
    private lateinit var settings: PortSettings
    private lateinit var homePage: ScrollView
    private lateinit var settingsPage: ScrollView
    private lateinit var homeTab: Button
    private lateinit var settingsTab: Button
    private lateinit var graphicsTab: Button
    private lateinit var controlsTab: Button
    private lateinit var graphicsPanel: LinearLayout
    private lateinit var controlsPanel: LinearLayout
    private lateinit var preview: FrameLayout
    private lateinit var status: TextView
    private lateinit var diagnostics: TextView
    private lateinit var play: Button
    private lateinit var select: Button
    private lateinit var driverStatus: TextView
    private lateinit var importDriver: Button
    private lateinit var toggleDriver: Button
    private lateinit var testVulkan: Button
    private var importingDriver = false
    private var selecting = false
    private var preparing = false

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        settings = PortSettings(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(PortStyle.background)
        }
        val pages = FrameLayout(this)
        root.addView(pages, LinearLayout.LayoutParams(-1, 0, 1f))
        homePage = scrollPage(pages, buildHome())
        settingsPage = scrollPage(pages, buildSettings())
        val menu = LinearLayout(this).apply {
            setBackgroundColor(PortStyle.surface)
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        homeTab = button("⌂   Início") { showSettings(false) }
        settingsTab = button("⚙   Configurações") { showSettings(true) }
        menu.addView(homeTab, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(5) })
        menu.addView(settingsTab, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginStart = dp(5) })
        root.addView(menu)
        setContentView(root)
        showSettings(state?.getBoolean("settings_page") == true)
        showControls(state?.getBoolean("controls_page") == true)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("settings_page", settingsPage.visibility == View.VISIBLE)
        outState.putBoolean("controls_page", controlsPanel.visibility == View.VISIBLE)
        super.onSaveInstanceState(outState)
    }

    private fun scrollPage(parent: FrameLayout, content: View) = ScrollView(this).apply {
        isFillViewport = true
        isVerticalScrollBarEnabled = false
        addView(content)
        parent.addView(this, FrameLayout.LayoutParams(-1, -1))
    }

    private fun buildHome(): LinearLayout = page().apply {
        val banner = FrameLayout(this@HomeActivity).apply {
            background = PortStyle.panel(this@HomeActivity, PortStyle.raised, 25)
            clipToOutline = true
            addView(RaceBanner(this@HomeActivity), FrameLayout.LayoutParams(-1, -1))
            val headline = LinearLayout(this@HomeActivity).apply {
                orientation = LinearLayout.VERTICAL
                val padding = dp(22)
                setPadding(padding, padding, padding, padding)
                addView(PortStyle.text(this@HomeActivity, "PISTONPORT", 13f, bold = true).apply {
                    letterSpacing = .18f
                })
                addView(PortStyle.text(this@HomeActivity, "Carros 3", 40f, bold = true).apply {
                    typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC)
                })
            }
            addView(headline, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        }
        addView(banner, LinearLayout.LayoutParams(-1, dp(190)).apply { bottomMargin = dp(18) })
        play = button("▶   Jogar", primary = true, click = ::playGame)
        addView(play, LinearLayout.LayoutParams(-1, dp(58)).apply { bottomMargin = dp(18) })
        val game = card("Pasta do jogo")
        status = PortStyle.text(this@HomeActivity, "", 14f, PortStyle.muted)
        game.addView(status)
        select = button("Selecionar pasta do jogo") {
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)
                openPicker()
            else requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), STORAGE)
        }
        append(game, select)
        addView(game)
    }

    private fun buildSettings(): LinearLayout = page().apply {
        addView(PortStyle.text(this@HomeActivity, "Configurações", 31f, bold = true))
        val tabs = LinearLayout(this@HomeActivity).apply {
            background = PortStyle.panel(this@HomeActivity, PortStyle.surface, 16)
            val padding = dp(4)
            setPadding(padding, padding, padding, padding)
        }
        graphicsTab = button("Gráficos") { showControls(false) }
        controlsTab = button("Controles") { showControls(true) }
        tabs.addView(graphicsTab, LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginEnd = dp(3) })
        tabs.addView(controlsTab, LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginStart = dp(3) })
        append(this, tabs)
        graphicsPanel = buildGraphics()
        controlsPanel = buildControls()
        append(this, graphicsPanel)
        append(this, controlsPanel)
    }

    private fun buildGraphics(): LinearLayout = column().apply {
        val rendering = card("Desempenho")
        rendering.addView(PortStyle.text(this@HomeActivity, "Resolução interna · 960 × 540", 14f, PortStyle.muted))
        choices(rendering, "Limite de FPS", listOf(30 to "30 FPS", 60 to "60 FPS"),
            { settings.fps }, { settings.fps = it })
        choices(rendering, "Filtragem de textura", listOf(0 to "Do jogo", 2 to "2×", 4 to "4×"),
            { settings.filtering }, { settings.filtering = it })
        addView(rendering)
        val driver = card("Driver Vulkan")
        driverStatus = PortStyle.text(this@HomeActivity, "", 14f, PortStyle.muted)
        driver.addView(driverStatus)
        importDriver = button("Selecionar Turnip (ZIP ou .so)") {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }, PICK_TURNIP)
        }
        append(driver, importDriver)
        toggleDriver = button("Usar driver do sistema") {
            TurnipDriver.select(this@HomeActivity, !TurnipDriver.enabled(this@HomeActivity))
            refresh()
        }
        append(driver, toggleDriver)
        testVulkan = button("Testar Vulkan 1.3") { startActivity(gameIntent()) }
        append(driver, testVulkan)
        append(this, driver)
        diagnostics = PortStyle.text(this@HomeActivity, "", 12f, PortStyle.muted).apply {
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
            val padding = dp(14)
            setPadding(padding, padding, padding, padding)
            background = PortStyle.panel(this@HomeActivity, PortStyle.surface, 18, PortStyle.border)
        }
        append(this, diagnostics)
    }

    private fun buildControls(): LinearLayout = column().apply {
        val card = card("Controles de corrida")
        val toggle = Switch(this@HomeActivity).apply {
            text = "Controles na tela"
            setTextColor(PortStyle.text)
            isChecked = settings.touch
            minHeight = dp(48)
            setOnCheckedChangeListener { _, checked -> settings.touch = checked }
        }
        card.addView(toggle)
        choices(card, "Tamanho", listOf(85 to "Pequeno", 100 to "Médio", 115 to "Grande"),
            { settings.size }, { settings.size = it; updatePreview() })
        choices(card, "Opacidade", listOf(55 to "55%", 70 to "70%", 85 to "85%", 100 to "100%"),
            { settings.opacity }, { settings.opacity = it; updatePreview() })
        addView(card)
        val previewFrame = card("Prévia interativa")
        preview = FrameLayout(this@HomeActivity).apply {
            background = PortStyle.panel(this@HomeActivity, PortStyle.background, 16, PortStyle.border)
            clipToOutline = true
            addView(RaceBanner(this@HomeActivity).apply { alpha = .25f }, FrameLayout.LayoutParams(-1, -1))
        }
        previewFrame.addView(preview, LinearLayout.LayoutParams(-1, dp(178)))
        append(this, previewFrame)
        updatePreview()
    }

    private fun updatePreview() {
        if (preview.childCount > 1) preview.removeViewAt(1)
        preview.addView(RaceControls(this, settings.size, settings.opacity, { _, _ -> }, preview = true),
            FrameLayout.LayoutParams(-1, -1))
    }

    private fun showSettings(visible: Boolean) {
        homePage.visibility = if (visible) View.GONE else View.VISIBLE
        settingsPage.visibility = if (visible) View.VISIBLE else View.GONE
        mark(homeTab, !visible)
        mark(settingsTab, visible)
    }

    private fun showControls(visible: Boolean) {
        graphicsPanel.visibility = if (visible) View.GONE else View.VISIBLE
        controlsPanel.visibility = if (visible) View.VISIBLE else View.GONE
        mark(graphicsTab, !visible)
        mark(controlsTab, visible)
    }

    private fun page() = column().apply { setPadding(dp(20), dp(18), dp(20), dp(24)) }

    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

    private fun card(title: String) = column().apply {
        val padding = dp(16)
        setPadding(padding, padding, padding, padding)
        background = PortStyle.panel(this@HomeActivity, PortStyle.surface, 19, PortStyle.border)
        addView(PortStyle.text(this@HomeActivity, title, 19f, bold = true),
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
    }

    private fun append(parent: LinearLayout, child: View) {
        parent.addView(child, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
    }

    private fun button(label: String, primary: Boolean = false, click: () -> Unit) = Button(this).apply {
        text = label
        textSize = if (primary) 18f else 14f
        isAllCaps = false
        setTextColor(PortStyle.text)
        minHeight = dp(48)
        minimumHeight = dp(48)
        minWidth = 0
        minimumWidth = 0
        setPadding(dp(5), 0, dp(5), 0)
        stateListAnimator = null
        background = PortStyle.action(this@HomeActivity, if (primary) PortStyle.red else PortStyle.raised,
            13, if (primary) Color.TRANSPARENT else PortStyle.border)
        setOnClickListener { click() }
    }

    private fun mark(button: Button, selected: Boolean) {
        button.isSelected = selected
        button.setTextColor(if (selected) PortStyle.text else PortStyle.muted)
        button.background = PortStyle.action(this, if (selected) 0xff79283a.toInt() else PortStyle.surface,
            12, if (selected) PortStyle.accent else Color.TRANSPARENT)
    }

    private fun choices(parent: LinearLayout, title: String, options: List<Pair<Int, String>>,
        selected: () -> Int, onSelect: (Int) -> Unit) {
        append(parent, PortStyle.text(this, title, 13f, PortStyle.muted, bold = true))
        val row = LinearLayout(this)
        val buttons = options.mapIndexed { index, (value, label) ->
            button(label) {
                onSelect(value)
                for (i in options.indices) mark(row.getChildAt(i) as Button, options[i].first == selected())
            }.also { choice ->
                row.addView(choice, LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                    if (index > 0) marginStart = dp(4)
                })
            }
        }
        buttons.forEachIndexed { index, choice -> mark(choice, options[index].first == selected()) }
        append(parent, row)
    }

    private fun playGame() {
        val folder = PistonSource.current(filesDir) ?: return
        preparing = true
        refresh()
        Thread {
            val image = runCatching { NativeBoot.prepare(applicationContext, folder) }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                preparing = false
                refresh()
                image.onSuccess { path ->
                    startActivity(gameIntent().putExtra("folder", folder.path).putExtra("image", path))
                }.onFailure { error ->
                    status.text = error.message ?: "Falha ao preparar a imagem do jogo."
                }
            }
        }.start()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val folder = PistonSource.current(filesDir)
        val idle = !selecting && !preparing && !importingDriver
        val turnip = TurnipDriver.enabled(this)
        select.isEnabled = idle
        play.isEnabled = idle && folder != null
        play.alpha = if (play.isEnabled) 1f else .6f
        importDriver.isEnabled = idle
        testVulkan.isEnabled = idle
        toggleDriver.isEnabled = idle
        toggleDriver.visibility = if (TurnipDriver.installed(this)) View.VISIBLE else View.GONE
        toggleDriver.text = if (turnip) "Usar driver do sistema" else "Ativar Turnip instalado"
        driverStatus.text = when {
            importingDriver -> "Instalando o driver…"
            turnip -> "Driver selecionado: Mesa Turnip"
            else -> "Driver selecionado: Vulkan do sistema"
        }
        status.text = when {
            preparing -> "Preparando a inicialização do jogo…"
            selecting -> "Verificando a pasta do jogo…"
            folder == null -> "Selecione a pasta extraída CUSA07027 v01.00 que contém eboot.bin."
            else -> "CUSA07027 v01.00 · ${folder.name}"
        }
        diagnostics.text = runCatching {
            RandomAccessFile(GameActivity.logFile(this), "r").use { stream ->
                val count = minOf(stream.length(), 8192L).toInt()
                stream.seek(stream.length() - count)
                ByteArray(count).also { stream.readFully(it) }.decodeToString().trim()
            }
        }.getOrDefault("")
        diagnostics.visibility = if (diagnostics.text.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun gameIntent() = settings.applyTo(Intent(this, GameActivity::class.java))
        .putExtra("turnip", TurnipDriver.enabled(this))

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
        if (result != RESULT_OK || selecting || preparing || importingDriver) return
        if (code == PICK_TURNIP) {
            importingDriver = true
            refresh()
            Thread {
                val installed = runCatching { TurnipDriver.install(applicationContext, uri) }
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    importingDriver = false
                    refresh()
                    installed.exceptionOrNull()?.let { driverStatus.text = it.message ?: "Falha ao instalar o Turnip." }
                }
            }.start()
            return
        }
        if (code != PICK_FOLDER) return
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

    private companion object {
        const val STORAGE = 1
        const val PICK_FOLDER = 2
        const val PICK_TURNIP = 3
    }
}

private object NativeBoot {
    @Synchronized
    fun prepare(context: Context, folder: File): String {
        val recipe = File(context.filesDir, "piston-recipes").apply { mkdirs() }
        val profile = File(context.filesDir, "pistonport").apply { mkdirs() }
        for (name in listOf("image.json", "cars3-runtime.elf")) {
            context.assets.open(name).use { input ->
                File(recipe, name).outputStream().use { output -> input.copyTo(output) }
            }
        }
        if (!Python.isStarted()) Python.start(AndroidPlatform(context))
        return Python.getInstance().getModule("native_boot")
            .callAttr("build", folder.path, profile.path, recipe.path).toString()
    }
}
