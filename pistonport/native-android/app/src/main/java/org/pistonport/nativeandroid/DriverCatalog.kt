package org.pistonport.nativeandroid

import android.content.Context
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import org.json.JSONArray

// Catalog discovery follows Bloodborne-Android's release/asset filtering model.
internal object DriverCatalog {
    private const val CATALOG_LIMIT = 6 * 1024 * 1024
    private const val DRIVER_LIMIT = 128 * 1024 * 1024
    enum class Source(val label: String, val repo: String, val kind: DriverKind = DriverKind.TURNIP) {
        STEVEN("StevenMXZ", "StevenMXZ/Adreno-Tools-Drivers"),
        PURPLE("Mesa Purple", "MrPurple666/purple-turnip"),
        ADRENO_TOOLS("AdrenoTools", "K11MCH1/AdrenoToolsDrivers"),
        BANNERS("Banners Turnip", "The412Banner/Banners-Turnip"),
        ZENITHBLUE("Zenithblue", "zenithblue-oss/panvk-kbase-android", DriverKind.PANVK),
        JIMVULKAN("JimVulkan", "JimVulkan/mali-panvk", DriverKind.PANVK),
        MINEGRAPH("MineGraph", "MineGraph-bilibili/panvk-Android", DriverKind.PANVK),
        SOULEXIT("soulexit", "soulexit/panvk-bifrost-soulexit", DriverKind.PANVK),
        FRIST_ONE("FristOneRR", "FristOneRR/FristOneRR-Panvk-Driver", DriverKind.PANVK)
    }

    private val bannersAndroid = Regex("""Turnip-v[\d.]+-\d{8}-r\d+\.zip""", RegexOption.IGNORE_CASE)
    private val bifrost = setOf(31, 51, 52, 71, 72, 76)
    private val valhall = setOf(57, 68, 77, 78)
    private val mineGraph = setOf(610, 615, 710, 715, 720)
    private val jimRevision = Regex("""mali-panvk-.*-r(\d+)\.zip""")
    private val fristModern = Regex("""panvk-fristonerr-v\.([3-9])\..+\.zip""")
    private val downloads = setOf("github.com", "release-assets.githubusercontent.com",
        "objects.githubusercontent.com", "github-releases.githubusercontent.com")

    data class Entry(val id: String, val name: String, val source: String, val url: String,
        val size: Long, val digest: String, val date: String, val kind: DriverKind)

    fun sourcesFor(gpu: GpuInfo): List<Source> = when (gpu.driverKind) {
        DriverKind.TURNIP -> Source.entries.filter { it.kind == DriverKind.TURNIP }
        DriverKind.PANVK -> Source.entries.filter { it.kind == DriverKind.PANVK && supportsMali(it, gpu.maliModel) }
        null -> emptyList()
    }

    private fun supportsMali(source: Source, model: Int?): Boolean = when (source) {
        Source.ZENITHBLUE -> model in bifrost || model in valhall || model in mineGraph ||
            model in setOf(310, 510, 620, 625, 725, 925, 1)
        Source.JIMVULKAN -> model in bifrost || model in valhall || model in mineGraph || model in setOf(925, 1)
        Source.MINEGRAPH -> model in mineGraph
        Source.SOULEXIT -> model in setOf(52, 72, 76)
        Source.FRIST_ONE -> model in valhall
        else -> false
    }

    fun fetch(source: Source, gpu: GpuInfo): List<Entry> {
        require(source in sourcesFor(gpu)) { "Esta fonte não oferece pacotes para a GPU detectada." }
        val items = mutableListOf<Entry>()
        val repo = source.repo
        open("https://api.github.com/repos/$repo/releases?per_page=30", setOf("api.github.com")) { stream ->
            val buffer = ByteArray(CATALOG_LIMIT + 1)
            val bytes = stream.readNBytes(buffer, 0, buffer.size)
            require(bytes <= CATALOG_LIMIT) { "Catálogo grande demais." }
            val releases = JSONArray(buffer.decodeToString(endIndex = bytes))
            for (i in 0 until releases.length()) {
                val release = releases.getJSONObject(i)
                if (release.optBoolean("draft") || release.optBoolean("prerelease")) continue
                val assets = release.optJSONArray("assets") ?: continue
                for (j in 0 until assets.length()) {
                    val asset = assets.getJSONObject(j)
                    val name = asset.optString("name")
                    val url = asset.optString("browser_download_url")
                    val size = asset.optLong("size")
                    val supported = if (source.kind == DriverKind.PANVK) {
                        panvkCompatible(source, name.lowercase(), gpu.maliModel)
                    } else {
                        val family = requireNotNull(gpu.adrenoFamily)
                        when (source) {
                            Source.PURPLE -> purpleCompatible(name, release.optString("body"), family)
                            Source.BANNERS -> family in 6..7 && bannersAndroid.matches(name)
                            else -> compatible(name, family)
                        }
                    }
                    if (!supported || size !in 1L..DRIVER_LIMIT.toLong() ||
                        !url.startsWith("https://github.com/$repo/releases/download/")) continue
                    val number = asset.optLong("id")
                    if (number <= 0) continue
                    items.add(Entry("github-$number", name.substringBeforeLast('.'),
                        source.label, url, size, asset.optString("digest"), asset.optString("updated_at"), source.kind))
                }
            }
        }
        return items.distinctBy(Entry::id).sortedByDescending(Entry::date).take(20)
    }

    private fun panvkCompatible(source: Source, name: String, model: Int?): Boolean = when (source) {
        Source.ZENITHBLUE -> name.endsWith(".adpkg.zip") &&
            (name.startsWith("mesa-panvk-android-") ||
                (model == 615 && name.startsWith("panvk-kbase-android-")))
        Source.JIMVULKAN -> {
            val revision = jimRevision.matchEntire(name)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            revision >= 9 || (revision == 8 && model !in setOf(925, 1)) || (revision in 1..4 && model in bifrost)
        }
        Source.MINEGRAPH -> name.startsWith("mesa") && "panvk" in name && name.endsWith(".zip")
        Source.SOULEXIT -> Regex("""soulexit-panvk-[\d.]+\.zip""").matches(name)
        Source.FRIST_ONE -> fristModern.matches(name) ||
            (model == 57 && name.startsWith("panvk-mali-g57-") && name.endsWith(".zip"))
        else -> false
    }

    private fun purpleCompatible(name: String, notes: String, family: Int): Boolean {
        val asset = name.lowercase()
        if (!asset.startsWith("turnip") || (!asset.endsWith(".adpkg") && !asset.endsWith(".zip"))) return false
        val familyMark = "a${family}xx"
        val declared = notes.lineSequence().firstOrNull { "unified:" in it.lowercase() }
        return if (declared != null) familyMark in declared.lowercase() else compatible(asset.removeSuffix(".adpkg") + if (asset.endsWith(".adpkg")) ".zip" else "", family)
    }

    private fun compatible(name: String, family: Int): Boolean {
        val lower = name.lowercase()
        if (!lower.startsWith("turnip") || !lower.endsWith(".zip")) return false
        val gen8 = "gen8" in lower || Regex("a8[0-9x]{2}").containsMatchIn(lower)
        val gen7 = "gen7" in lower || Regex("a7[0-9x]{2}").containsMatchIn(lower)
        val gen6 = "gen6" in lower || Regex("a6[0-9x]{2}").containsMatchIn(lower)
        return when {
            gen8 -> family == 8
            gen7 && !gen6 -> family == 7
            gen6 && !gen7 -> family == 6
            else -> family in 6..7
        }
    }

    fun install(context: Context, entry: Entry, report: (String) -> Unit) {
        val temporary = File.createTempFile("vulkan-download-", ".zip", context.cacheDir)
        try {
            val sha = MessageDigest.getInstance("SHA-256")
            open(entry.url, downloads) { input ->
                temporary.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    var lastProgress = 0
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        require(total <= entry.size) { "O download tem tamanho inesperado." }
                        sha.update(buffer, 0, read)
                        output.write(buffer, 0, read)
                        val progress = (total * 100 / entry.size).toInt()
                        if (progress >= lastProgress + 5) {
                            lastProgress = progress
                            report("Baixando ${entry.name} · $progress%")
                        }
                    }
                    require(total == entry.size) { "O download do driver está incompleto." }
                }
            }
            report("Verificando e instalando ${entry.name}…")
            val digest = sha.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
            if (entry.digest.startsWith("sha256:")) {
                require(entry.digest.equals("sha256:$digest", ignoreCase = true)) { "O SHA-256 do driver não confere." }
            }
            temporary.inputStream().use { VulkanDriver.install(context, entry.kind, it, entry.id, entry.name) }
        } finally {
            temporary.delete()
        }
    }

    private fun open(address: String, allowed: Set<String>, read: (InputStream) -> Unit) {
        var url = URL(address)
        repeat(5) {
            check(url.protocol == "https" && url.host in allowed && url.userInfo == null &&
                url.port in listOf(-1, 443)) { "Origem do download inválida." }
            val connection = url.openConnection() as HttpURLConnection
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 12000
                connection.readTimeout = 20000
                connection.setRequestProperty("User-Agent", "PistonPort-Android")
                connection.setRequestProperty("Accept-Encoding", "identity")
                val status = connection.responseCode
                if (status in listOf(301, 302, 303, 307, 308)) {
                    url = URL(url, connection.getHeaderField("Location") ?: error("Redirecionamento inválido."))
                } else {
                    check(status == 200) { "GitHub retornou HTTP $status." }
                    connection.inputStream.use(read)
                    return
                }
            } finally {
                connection.disconnect()
            }
        }
        error("Redirecionamentos demais no GitHub.")
    }
}
