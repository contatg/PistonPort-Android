package org.pistonport.nativeandroid

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
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
    private lateinit var creditsPage: ScrollView
    private lateinit var homeTab: Button
    private lateinit var settingsTab: Button
    private lateinit var creditsTab: Button
    private lateinit var releaseButton: Button
    private var availableUpdate: ReleaseUpdates.Update? = null
    private lateinit var graphicsTab: Button
    private lateinit var controlsTab: Button
    private lateinit var graphicsPanel: LinearLayout
    private lateinit var controlsPanel: LinearLayout
    private lateinit var status: TextView
    private lateinit var diagnostics: TextView
    private lateinit var play: Button
    private lateinit var select: Button
    private lateinit var driverStatus: TextView
    private lateinit var importDriver: Button
    private lateinit var openHub: Button
    private lateinit var settingsGpu: TextView
    private lateinit var compatibilityButton: Button
    private var hardwareNotice: AlertDialog? = null
    private var gpuInfo: GpuInfo? = null
    private val catalogs = mutableMapOf<DriverCatalog.Source, List<DriverCatalog.Entry>>()
    private var resumed = false
    private val installingDriver get() = DriverInstall.status?.running == true
    private var hubUpdate: (() -> Unit)? = null
    private var lastHubSource: DriverCatalog.Source? = null
    private var selecting = false
    private var preparing = false
    private var frameAssetBusy = false
    private lateinit var frameAssets: TextView
    private lateinit var importLossless: Button
    private lateinit var downloadMako: Button

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        settings = PortSettings(this)
        lastHubSource = DriverCatalog.Source.entries.firstOrNull { it.name == state?.getString("hub_source") }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(PortStyle.background)
        }
        val pages = FrameLayout(this)
        root.addView(pages, LinearLayout.LayoutParams(-1, 0, 1f))
        homePage = scrollPage(pages, buildHome())
        settingsPage = scrollPage(pages, buildSettings())
        creditsPage = scrollPage(pages, page().apply {
            addView(PortStyle.text(this@HomeActivity, getString(R.string.credits_tab), 31f, bold = true))
            append(this, card("contatg").apply {
                addView(PortStyle.text(this@HomeActivity, "GitHub · @contatg", 15f, PortStyle.muted))
                addView(PortStyle.text(this@HomeActivity, getString(R.string.project_about), 15f, PortStyle.muted))
                append(this, button(getString(R.string.project_github)) {
                    openRelease("https://github.com/contatg/PistonPort-Android")
                })
            })
        })
        val menu = LinearLayout(this).apply {
            setBackgroundColor(PortStyle.surface)
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        homeTab = button(getString(R.string.home_tab)) { showPage(homePage) }
        settingsTab = button(getString(R.string.settings_tab)) { showPage(settingsPage) }
        creditsTab = button(getString(R.string.credits_tab)) { showPage(creditsPage) }
        for (tab in listOf(homeTab, settingsTab, creditsTab)) {
            menu.addView(tab, LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                marginStart = dp(3)
                marginEnd = dp(3)
            })
        }
        root.addView(menu)
        setContentView(root)
        showPage(when {
            state?.getBoolean("credits_page") == true -> creditsPage
            state?.getBoolean("settings_page") == true -> settingsPage
            else -> homePage
        })
        showControls(state?.getBoolean("controls_page") == true)
        checkForUpdates()
        Thread {
            val result = runCatching { GpuInfo.detect() }.getOrElse {
                GpuInfo("", "", false)
            }
            runOnUiThread {
                if (!isDestroyed && !isFinishing) {
                    gpuInfo = result
                    refresh()
                    showHardwareNotice()
                }
            }
        }.start()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("hub_source", lastHubSource?.name)
        outState.putBoolean("settings_page", settingsPage.visibility == View.VISIBLE)
        outState.putBoolean("credits_page", creditsPage.visibility == View.VISIBLE)
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
        play = button(getString(R.string.play), primary = true, click = ::playGame)
        addView(play, LinearLayout.LayoutParams(-1, dp(58)).apply { bottomMargin = dp(18) })
        val game = card(getString(R.string.game_folder))
        status = PortStyle.text(this@HomeActivity, "", 14f, PortStyle.muted)
        game.addView(status)
        select = button(getString(R.string.pick_folder)) {
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)
                openPicker()
            else requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), STORAGE)
        }
        append(game, select)
        addView(game)
        releaseButton = button(getString(R.string.releases)) { openRelease(availableUpdate?.download ?: ReleaseUpdates.releasesPage) }
        append(this, releaseButton)
    }

    private fun checkForUpdates() {
        val version = packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            .versionName ?: return
        Thread {
            val update = runCatching { ReleaseUpdates.check(version) }.getOrNull() ?: return@Thread
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    availableUpdate = update
                    releaseButton.text = getString(R.string.download_update, update.version)
                }
            }
        }.start()
    }

    private fun openRelease(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { android.widget.Toast.makeText(this, getString(R.string.no_browser), android.widget.Toast.LENGTH_SHORT).show() }
    }

    private fun buildSettings(): LinearLayout = page().apply {
        addView(PortStyle.text(this@HomeActivity, getString(R.string.settings_tab), 31f, bold = true))
        val tabs = LinearLayout(this@HomeActivity).apply {
            background = PortStyle.panel(this@HomeActivity, PortStyle.surface, 16)
            val padding = dp(4)
            setPadding(padding, padding, padding, padding)
        }
        graphicsTab = button(getString(R.string.graphics)) { showControls(false) }
        controlsTab = button(getString(R.string.controls)) { showControls(true) }
        tabs.addView(graphicsTab, LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginEnd = dp(3) })
        tabs.addView(controlsTab, LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginStart = dp(3) })
        append(this, tabs)
        graphicsPanel = buildGraphics()
        controlsPanel = buildControls()
        append(this, graphicsPanel)
        append(this, controlsPanel)
    }

    private fun buildGraphics(): LinearLayout = column().apply {
        addView(PortStyle.text(this@HomeActivity,
            getString(R.string.in_game_hint),
            13f, PortStyle.muted))
        val rendering = card(getString(R.string.graphics_performance))
        append(rendering, Switch(this@HomeActivity).apply {
            text = getString(R.string.potato)
            setTextColor(PortStyle.text)
            minHeight = dp(48)
            isChecked = settings.liteMode
            setOnCheckedChangeListener { _, checked -> settings.liteMode = checked }
        })
        append(rendering, PortStyle.text(this@HomeActivity,
            getString(R.string.potato_detail),
            13f, PortStyle.muted))
        menuChoice(rendering, getString(R.string.internal_resolution), listOf(
            360 to "360p", 480 to "480p", 540 to "540p", 720 to "720p",
            900 to "900p", 1080 to "1080p", 1440 to "1440p"),
            { settings.resolution }, { settings.resolution = it })
        graphicsChoice(rendering, getString(R.string.output_resolution), "output", 1080,
            listOf(540 to "540p", 720 to "720p", 900 to "900p", 1080 to "1080p", 1440 to "1440p"))
        graphicsChoice(rendering, getString(R.string.aspect), "aspect", 0,
            aspectRatios.mapIndexed { index, aspect -> index to if (index == 0) getString(R.string.automatic) else aspect })
        graphicsChoice(rendering, "Upscaler", "upscaler", 0,
            listOf(0 to getString(R.string.off), 1 to "FSR 3.1", 2 to "TAA", 3 to "GSR 2", 4 to "FSR 1"))
        graphicsChoice(rendering, getString(R.string.upscaler_quality), "preset", 3, listOf(
            0 to getString(R.string.native_resolution), 5 to getString(R.string.ultra_quality), 1 to getString(R.string.quality),
            2 to getString(R.string.balanced), 3 to getString(R.string.performance), 4 to getString(R.string.ultra_performance)))
        graphicsSlider(rendering, getString(R.string.sharpness), "sharpness", 0, 100, 20)
        menuChoice(rendering, getString(R.string.fps_limit), listOf(
            30 to "30 FPS", 40 to "40 FPS", 45 to "45 FPS", 60 to "60 FPS", 0 to getString(R.string.fps_unlimited)),
            { settings.fps }, { settings.fps = it })
        graphicsToggle(rendering, "VSync", "vsync", true)
        append(rendering, Switch(this@HomeActivity).apply {
            text = getString(R.string.performance_hud)
            setTextColor(PortStyle.text)
            minHeight = dp(48)
            isChecked = settings.performanceHud
            setOnCheckedChangeListener { _, checked -> settings.performanceHud = checked }
        })
        append(this, rendering)

        val effects = card(getString(R.string.effects))
        graphicsToggle(effects, "Bloom", "bloom", true)
        graphicsToggle(effects, getString(R.string.dof), "dof", true)
        graphicsToggle(effects, getString(R.string.motion_blur), "motion_blur", true)
        graphicsToggle(effects, getString(R.string.shadows), "shadows", true)
        graphicsToggle(effects, getString(R.string.async_shaders), "async", true)
        graphicsChoice(effects, getString(R.string.texture_quality), "texture", 0,
            listOf(0 to getString(R.string.quality_high), 1 to getString(R.string.quality_medium), 2 to getString(R.string.quality_low), 3 to getString(R.string.quality_very_low)))
        choices(effects, getString(R.string.texture_filter), listOf(0 to getString(R.string.game_default), 2 to "2×", 4 to "4×", 8 to "8×", 16 to "16×"),
            { settings.filtering }, { settings.filtering = it })
        append(this, effects)

        val frame = card(getString(R.string.frame_generation))
        graphicsChoice(frame, getString(R.string.method), "framegen", 0, listOf(0 to getString(R.string.off), 1 to "FSR 3.1 · 2×",
            2 to "Piston Frame · 2×", 3 to "Piston Frame · 3×", 4 to "Piston Frame · 4×", 5 to getString(R.string.blend)))
        graphicsChoice(frame, getString(R.string.frame_model), "fg_model", 0,
            listOf(0 to "Lossless Scaling", 1 to "MAKO-FG experimental"))
        graphicsSlider(frame, getString(R.string.frame_motion), "fg_flow", 25, 100, 75)
        graphicsToggle(frame, getString(R.string.performance_mode), "fg_fast", true)
        graphicsToggle(frame, getString(R.string.half_precision), "fg_fp16", true)
        frameAssets = PortStyle.text(this@HomeActivity, "", 13f, PortStyle.muted)
        append(frame, frameAssets)
        importLossless = button(getString(R.string.import_lossless)) {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }, PICK_LOSSLESS)
        }
        append(frame, importLossless)
        downloadMako = button(getString(R.string.download_mako)) {
            AlertDialog.Builder(this@HomeActivity).setTitle("MAKO-FG de Eugenio Segala")
                .setMessage(getString(R.string.mako_license))
                .setNegativeButton(getString(R.string.cancel), null)
                .setPositiveButton(getString(R.string.download)) { _, _ -> frameAssetTask { FrameGenAssets.installMako(graphicsProfile()) } }
                .show()
        }
        append(frame, downloadMako)
        append(this, frame)
        val driver = card(getString(R.string.driver_hub))
        settingsGpu = PortStyle.text(this@HomeActivity, "", 14f, PortStyle.muted)
        driver.addView(settingsGpu)
        driverStatus = PortStyle.text(this@HomeActivity, "", 14f, PortStyle.muted)
        driver.addView(driverStatus)
        importDriver = button(getString(R.string.import_driver)) {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }, PICK_DRIVER)
        }
        append(driver, importDriver)
        openHub = button(getString(R.string.open_hub), primary = true) { showDriverHub(lastHubSource) }
        append(driver, openHub)
        compatibilityButton = button(getString(R.string.compatibility_review)) { showHardwareNotice(manual = true) }
        append(driver, compatibilityButton)
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
        val card = card(getString(R.string.race_controls))
        val toggle = Switch(this@HomeActivity).apply {
            text = getString(R.string.touch_controls)
            setTextColor(PortStyle.text)
            isChecked = settings.touch
            minHeight = dp(48)
            setOnCheckedChangeListener { _, checked -> settings.touch = checked }
        }
        card.addView(toggle)
        choices(card, getString(R.string.opacity), listOf(55 to "55%", 70 to "70%", 85 to "85%", 100 to "100%"),
            { settings.opacity }, { settings.opacity = it })
        append(card, button(getString(R.string.edit_fullscreen)) {
            startActivity(Intent(this@HomeActivity, TouchLayoutActivity::class.java))
        })
        addView(card)
    }

    private fun showPage(active: ScrollView) {
        for ((page, tab) in listOf(homePage to homeTab, settingsPage to settingsTab, creditsPage to creditsTab)) {
            val selected = active == page
            page.visibility = if (selected) View.VISIBLE else View.GONE
            mark(tab, selected)
        }
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

    private fun menuChoice(parent: LinearLayout, title: String, options: List<Pair<Int, String>>,
        selected: () -> Int, save: (Int) -> Unit) {
        fun label() = "$title · " + (options.firstOrNull { it.first == selected() } ?: options.first()).second
        lateinit var control: Button
        control = button(label()) {
            AlertDialog.Builder(this).setTitle(title)
                .setSingleChoiceItems(options.map { it.second }.toTypedArray(),
                    options.indexOfFirst { it.first == selected() }) { dialog, index ->
                    save(options[index].first)
                    control.text = label()
                    dialog.dismiss()
                }.setNegativeButton(getString(R.string.close), null).show()
        }
        append(parent, control)
    }

    private fun graphicsChoice(parent: LinearLayout, title: String, key: String, default: Int,
        options: List<Pair<Int, String>>) =
        menuChoice(parent, title, options, { settings.choice(key, default) }, { settings.setChoice(key, it) })

    private fun graphicsToggle(parent: LinearLayout, title: String, key: String, default: Boolean) {
        append(parent, Switch(this).apply {
            text = title
            setTextColor(PortStyle.text)
            minHeight = dp(42)
            isChecked = settings.toggle(key, default)
            setOnCheckedChangeListener { _, checked -> settings.setToggle(key, checked) }
        })
    }

    private fun graphicsSlider(parent: LinearLayout, title: String, key: String,
        min: Int, max: Int, default: Int) {
        val initial = settings.choice(key, default)
        val caption = PortStyle.text(this, "$title · $initial%", 14f, PortStyle.muted)
        append(parent, caption)
        append(parent, SeekBar(this).apply {
            this.max = max - min
            progress = initial - min
            contentDescription = title
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar, value: Int, fromUser: Boolean) {
                    caption.text = "$title · ${min + value}%"
                    if (fromUser) settings.setChoice(key, min + value)
                }

                override fun onStartTrackingTouch(bar: SeekBar) = Unit
                override fun onStopTrackingTouch(bar: SeekBar) = Unit
            })
        })
    }

    private fun graphicsProfile() = File(filesDir, "pistonport")

    private fun frameAssetTask(work: () -> Unit) {
        frameAssetBusy = true
        refresh()
        Thread {
            val result = runCatching(work)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                frameAssetBusy = false
                refresh()
                result.exceptionOrNull()?.let { frameAssets.text = it.message ?: "Falha ao instalar o modelo." }
            }
        }.start()
    }

    private fun playGame() {
        val folder = PistonSource.current(filesDir) ?: return
        val launch = gameIntent().putExtra("folder", folder.path)
        val selectedResolution = settings.launchResolution
        val generation = settings.launchFrameGeneration
        val profile = graphicsProfile()
        if (generation in 2..4) {
            val mako = settings.choice("fg_model", 0) == 1
            val ready = if (mako) FrameGenAssets.makoInstalled(profile) else FrameGenAssets.lossless(profile).isFile
            if (!ready) {
                panelDialog(card("Piston Frame").apply {
                    addView(PortStyle.text(this@HomeActivity,
                        if (mako) getString(R.string.missing_model_mako)
                        else getString(R.string.missing_model_dll), 15f, PortStyle.muted))
                }).show()
                return
            }
        }
        preparing = true
        refresh()
        Thread {
            val image = runCatching { NativeBoot.prepare(applicationContext, folder, selectedResolution) }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                preparing = false
                refresh()
                image.onSuccess { path ->
                    startActivity(launch.putExtra("image", path))
                }.onFailure { error ->
                    status.text = error.message ?: "Falha ao preparar a imagem do jogo."
                }
            }
        }.start()
    }

    override fun onStart() {
        super.onStart()
        DriverInstall.onChange = ::refreshDriverInstall
        refreshDriverInstall()
    }

    override fun onStop() {
        DriverInstall.onChange = null
        super.onStop()
    }

    private fun refreshDriverInstall() {
        refresh()
        hubUpdate?.invoke()
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        refresh()
        showHardwareNotice()
    }

    override fun onPause() {
        resumed = false
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) showHardwareNotice()
    }

    private fun showHardwareNotice(manual: Boolean = false) {
        val info = gpuInfo ?: return
        if ((!manual && settings.hardwareNoticeAcknowledged) || !resumed ||
            isFinishing || isDestroyed || hardwareNotice != null) return
        val sources = DriverCatalog.sourcesFor(info)
        val content = card(if (info.stockReady) getString(R.string.vulkan_available) else getString(R.string.vulkan_compatibility))
        val kind = info.driverKind
        val suggestPanvk = kind == DriverKind.PANVK && VulkanDriver.selected(this, kind) == null
        val message = if (suggestPanvk && info.stockReady && sources.isNotEmpty())
            info.summary(this) + "\n" + getString(R.string.panvk_optional)
        else info.summary(this)
        content.addView(PortStyle.text(this, message, 15f, PortStyle.muted))
        val dialog = panelDialog(content)
        hardwareNotice = dialog
        dialog.setOnDismissListener { hardwareNotice = null }
        dialog.setOnCancelListener { if (!manual) settings.hardwareNoticeAcknowledged = true }
        if (kind != null && sources.isNotEmpty() && (suggestPanvk || !info.stockReady)) {
            append(content, button(getString(R.string.open_kind_hub, kind.label), primary = true) {
                settings.hardwareNoticeAcknowledged = true
                dialog.dismiss()
                showDriverHub(sources.first())
            })
        }
        append(content, button(getString(R.string.understood), primary = sources.isEmpty() || info.stockReady && !suggestPanvk) {
            settings.hardwareNoticeAcknowledged = true
            dialog.dismiss()
        })
        dialog.show()
        if (!manual) settings.hardwareNoticeAcknowledged = true
    }

    private fun panelDialog(content: View): AlertDialog =
        AlertDialog.Builder(this).setView(content).create().apply {
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }

    private fun refresh() {
        val folder = PistonSource.current(filesDir)
        val idle = !selecting && !preparing && !installingDriver && !frameAssetBusy
        val kind = gpuInfo?.driverKind
        val selectedDriver = VulkanDriver.selected(this, kind)
        val installed = VulkanDriver.list(this, kind)
        val gpuLabel = gpuInfo?.summary(this) ?: getString(R.string.gpu_detecting)
        settingsGpu.text = gpuLabel
        select.isEnabled = idle
        play.isEnabled = idle && folder != null && gpuInfo != null
        play.alpha = if (play.isEnabled) 1f else .6f
        importDriver.visibility = if (kind == null) View.GONE else View.VISIBLE
        importDriver.text = getString(R.string.import_kind, kind?.label.orEmpty())
        importDriver.isEnabled = idle && kind != null
        openHub.text = kind?.let { getString(R.string.open_kind_hub, it.label) } ?: getString(R.string.open_hub)
        openHub.isEnabled = gpuInfo != null && (idle || installingDriver)
        compatibilityButton.isEnabled = gpuInfo != null
        importLossless.isEnabled = idle
        downloadMako.isEnabled = idle && !FrameGenAssets.makoInstalled(graphicsProfile())
        frameAssets.text = if (frameAssetBusy) getString(R.string.installing_framegen) else {
            val lossless = if (FrameGenAssets.lossless(graphicsProfile()).isFile) getString(R.string.imported) else getString(R.string.pending)
            val mako = if (FrameGenAssets.makoInstalled(graphicsProfile())) getString(R.string.installed) else getString(R.string.pending)
            "Lossless.dll · $lossless\nMAKO-FG · $mako"
        }
        val driverName = installed.firstOrNull { it.id == selectedDriver }?.name ?: getString(R.string.stock_driver)
        driverStatus.text = listOfNotNull(getString(R.string.driver_selected, driverName), DriverInstall.status?.message)
            .joinToString("\n")
        status.text = when {
            preparing -> getString(R.string.starting_game)
            selecting -> getString(R.string.checking_folder)
            folder == null -> getString(R.string.folder_help)
            else -> "CUSA07027 v01.00 · ${folder.name}"
        }
        diagnostics.text = readLog(GameActivity.logFile(this))
        diagnostics.visibility = if (diagnostics.text.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun readLog(file: File): String = runCatching {
            RandomAccessFile(file, "r").use { stream ->
                val count = minOf(stream.length(), 8192L).toInt()
                stream.seek(stream.length() - count)
                ByteArray(count).also { stream.readFully(it) }.decodeToString().trim()
            }
    }.getOrDefault("")

    private fun showDriverHub(preferred: DriverCatalog.Source?) {
        val gpu = gpuInfo ?: return
        val kind = gpu.driverKind
        val root = card(kind?.let { getString(R.string.kind_hub, it.label) } ?: getString(R.string.driver_hub))
        root.addView(PortStyle.text(this, gpu.displayName(this), 14f, PortStyle.muted))
        val tabs = column()
        append(root, tabs)
        val installStatus = PortStyle.text(this, "", 14f, PortStyle.muted).apply {
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            maxLines = 4
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        append(root, installStatus)
        val results = column()
        val scroll = ScrollView(this).apply {
            addView(results)
            isFillViewport = true
        }
        val height = (resources.configuration.screenHeightDp - 365).coerceIn(125, 420)
        root.addView(scroll, LinearLayout.LayoutParams(-1, dp(height)).apply { topMargin = dp(12) })
        val dialog = panelDialog(root)
        val sources = listOf(null) + DriverCatalog.sourcesFor(gpu)
        val buttons = mutableListOf<Button>()
        var active: DriverCatalog.Source? = null
        val loading = mutableSetOf<DriverCatalog.Source>()
        val refreshCatalog = button(getString(R.string.refresh)) {}
        var lastInstallStatus = DriverInstall.status

        fun message(label: String) {
            append(results, PortStyle.text(this, label, 14f, PortStyle.muted))
        }

        fun selectDriver(id: String) {
            kind?.let { VulkanDriver.select(this, it, id.ifBlank { null }) }
            DriverInstall.clearResult()
            refreshDriverInstall()
            // A manual selection can change even when there is no prior installation status.
            dialog.dismiss()
        }

        fun addDriver(id: String, name: String, detail: String, action: String, run: () -> Unit) {
            val row = card(name)
            row.addView(PortStyle.text(this, detail, 13f, PortStyle.muted))
            val selectedNow = id == VulkanDriver.selected(this, kind).orEmpty()
            append(row, button(if (selectedNow) getString(R.string.selected) else action,
                primary = !selectedNow && action == getString(R.string.download), click = { if (!installingDriver) run() }).apply {
                isEnabled = !selectedNow && !installingDriver
                if (selectedNow) mark(this, true)
            })
            append(results, row)
        }

        fun display(entries: List<DriverCatalog.Entry>) {
            results.removeAllViews()
            if (entries.isEmpty()) message(getString(R.string.no_drivers))
            val installedIds = VulkanDriver.list(this, kind).map { it.id }.toSet()
            for (entry in entries) {
                val size = "%.1f MiB".format(entry.size / 1048576.0)
                val present = entry.id in installedIds
                val details = "${entry.source} · $size" + if (present) " · Instalado" else ""
                addDriver(entry.id, entry.name, details, if (present) getString(R.string.activate) else getString(R.string.download)) {
                    if (present) selectDriver(entry.id)
                    else {
                        val context = applicationContext
                        DriverInstall.start(entry.name, getString(R.string.download_driver, entry.name)) { report ->
                            DriverCatalog.install(context, entry, report)
                        }
                    }
                }
            }
        }

        fun show(source: DriverCatalog.Source?, force: Boolean = false, keepPosition: Boolean = false) {
            active = source
            lastHubSource = source
            val position = if (keepPosition) scroll.scrollY else 0
            buttons.forEachIndexed { index, button -> mark(button, sources[index] == source) }
            results.removeAllViews()
            scroll.scrollTo(0, 0)
            refreshCatalog.isEnabled = source != null && !installingDriver
            if (source == null) {
                addDriver("", getString(R.string.stock_driver), getString(R.string.android_driver), getString(R.string.activate)) { selectDriver("") }
                for (entry in VulkanDriver.list(this, kind)) {
                    addDriver(entry.id, entry.name, getString(R.string.driver_installed, kind?.label.orEmpty()), getString(R.string.activate)) { selectDriver(entry.id) }
                }
                if (sources.size == 1) message(getString(R.string.no_catalog))
                return
            }
            if (!force) catalogs[source]?.let {
                display(it)
                if (position > 0) scroll.post { if (active == source) scroll.scrollTo(0, position) }
                return
            }
            refreshCatalog.isEnabled = false
            message(getString(R.string.loading_catalog, source.label))
            if (!loading.add(source)) return
            Thread {
                val result = runCatching { DriverCatalog.fetch(source, gpu) }
                runOnUiThread {
                    loading.remove(source)
                    result.onSuccess { catalogs[source] = it }
                    if (isFinishing || isDestroyed || !dialog.isShowing || active != source) return@runOnUiThread
                    refreshCatalog.isEnabled = !installingDriver
                    result.onSuccess(::display).onFailure {
                        results.removeAllViews()
                        message(getString(R.string.catalog_failed, source.label, it.message.orEmpty()))
                    }
                }
            }.start()
        }

        for (pair in sources.chunked(2)) {
            val row = LinearLayout(this)
            for ((index, source) in pair.withIndex()) {
                val tab = button(source?.label ?: getString(R.string.installed_title)) { if (!installingDriver) show(source) }
                buttons.add(tab)
                row.addView(tab, LinearLayout.LayoutParams(0, -2, 1f).apply {
                    if (index > 0) marginStart = dp(6)
                })
            }
            append(tabs, row)
        }
        refreshCatalog.setOnClickListener { if (!installingDriver) show(active, force = true) }
        val actions = LinearLayout(this)
        actions.addView(refreshCatalog, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(6) })
        actions.addView(button(getString(R.string.close)) { dialog.dismiss() }, LinearLayout.LayoutParams(0, -2, 1f))
        append(root, actions)
        hubUpdate = {
            val status = DriverInstall.status
            installStatus.text = status?.message.orEmpty()
            installStatus.visibility = if (status == null) View.GONE else View.VISIBLE
            installStatus.setTextColor(if (status?.failed == true) PortStyle.accent else PortStyle.muted)
            val redraw = (lastInstallStatus?.running == true) != installingDriver ||
                (lastInstallStatus != null && status == null)
            lastInstallStatus = status
            buttons.forEach { it.isEnabled = !installingDriver }
            if (redraw) show(active, keepPosition = true)
        }
        dialog.setOnDismissListener { hubUpdate = null }
        dialog.show()
        show(preferred?.takeIf { it in sources })
        hubUpdate?.invoke()
    }

    private fun gameIntent(): Intent {
        val kind = gpuInfo?.driverKind
        return settings.applyTo(Intent(this, GameActivity::class.java))
            .putExtra("driver_kind", kind?.name)
            .putExtra("driver_id", VulkanDriver.selected(this, kind))
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
        if (result != RESULT_OK || selecting || preparing || installingDriver) return
        if (code == PICK_LOSSLESS) {
            frameAssetTask { FrameGenAssets.importDll(applicationContext, graphicsProfile(), uri) }
            return
        }
        if (code == PICK_DRIVER) {
            val context = applicationContext
            val kind = gpuInfo?.driverKind ?: return
            DriverInstall.start("${kind.label} importado", "Importando driver…") {
                VulkanDriver.install(context, kind, uri)
            }
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
        const val PICK_DRIVER = 3
        const val PICK_LOSSLESS = 4
    }
}

private object NativeBoot {
    @Synchronized
    fun prepare(context: Context, folder: File, resolution: Int): String {
        val recipe = File(context.filesDir, "piston-recipes").apply { mkdirs() }
        val profile = File(context.filesDir, "pistonport").apply { mkdirs() }
        for (name in listOf("image.json", "cars3-runtime.elf")) {
            context.assets.open(name).use { input ->
                File(recipe, name).outputStream().use { output -> input.copyTo(output) }
            }
        }
        if (!Python.isStarted()) Python.start(AndroidPlatform(context))
        return Python.getInstance().getModule("native_boot")
            .callAttr("build", folder.path, profile.path, recipe.path,
                if (resolution == 1080) "native" else "${resolution}p").toString()
    }
}
