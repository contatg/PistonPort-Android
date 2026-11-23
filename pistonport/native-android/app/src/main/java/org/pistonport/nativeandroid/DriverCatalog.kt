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
    enum class Source(val label: String, val repo: String) {
        BLOODBORNE("Bloodborne", "ducvd89/Bloodborne-Android"),
        ADRENO_TOOLS("AdrenoTools", "K11MCH1/AdrenoToolsDrivers"),
        BANNERS("Banners Turnip", "The412Banner/Banners-Turnip")
    }

    private val bannersAndroid = Regex("""Turnip-v[\d.]+-\d{8}-r\d+\.zip""", RegexOption.IGNORE_CASE)
    private val downloads = setOf("github.com", "release-assets.githubusercontent.com",
        "objects.githubusercontent.com", "github-releases.githubusercontent.com")

    data class Entry(val id: String, val name: String, val source: String, val url: String,
        val size: Long, val digest: String, val date: String)

    fun fetch(source: Source, family: Int): List<Entry> {
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
                if (source == Source.BLOODBORNE &&
                    !release.optString("tag_name").startsWith("turnip-android-")) continue
                val assets = release.optJSONArray("assets") ?: continue
                for (j in 0 until assets.length()) {
                    val asset = assets.getJSONObject(j)
                    val name = asset.optString("name")
                    val url = asset.optString("browser_download_url")
                    val size = asset.optLong("size")
                    val supported = if (source == Source.BANNERS)
                        family in 6..7 && bannersAndroid.matches(name) else compatible(name, family)
                    if (!supported || size !in 1L..DRIVER_LIMIT.toLong() ||
                        !url.startsWith("https://github.com/$repo/releases/download/")) continue
                    val number = asset.optLong("id")
                    if (number <= 0) continue
                    items.add(Entry("github-$number", name.dropLast(4),
                        source.label, url, size, asset.optString("digest"), asset.optString("updated_at")))
                }
            }
        }
        return items.distinctBy(Entry::id).sortedByDescending(Entry::date).take(20)
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

    fun install(context: Context, entry: Entry) {
        val temporary = File.createTempFile("turnip-download-", ".zip", context.cacheDir)
        try {
            val sha = MessageDigest.getInstance("SHA-256")
            open(entry.url, downloads) { input ->
                temporary.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        require(total <= entry.size) { "O download tem tamanho inesperado." }
                        sha.update(buffer, 0, read)
                        output.write(buffer, 0, read)
                    }
                    require(total == entry.size) { "O download do driver está incompleto." }
                }
            }
            val digest = sha.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
            if (entry.digest.startsWith("sha256:")) {
                require(entry.digest.equals("sha256:$digest", ignoreCase = true)) { "O SHA-256 do driver não confere." }
            }
            temporary.inputStream().use { TurnipDriver.install(context, it, entry.id, entry.name) }
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
