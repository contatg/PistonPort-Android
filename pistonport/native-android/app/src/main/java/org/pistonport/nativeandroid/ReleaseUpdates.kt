package org.pistonport.nativeandroid

import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

internal object ReleaseUpdates {
    const val releasesPage = "https://github.com/contatg/PistonPort-Android/releases"
    private const val endpoint = "https://api.github.com/repos/contatg/PistonPort-Android/releases/latest"
    private const val responseLimit = 256 * 1024
    private val versionTag = Regex("""v?(\d{1,6})\.(\d{1,6})\.(\d{1,6})""", RegexOption.IGNORE_CASE)
    private val apkName = Regex("""PistonPort-Android-[A-Za-z0-9_.-]*arm64[A-Za-z0-9_.-]*\.apk""",
        RegexOption.IGNORE_CASE)

    data class Update(val version: String, val download: String)

    fun check(currentVersion: String): Update? {
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 8000
            connection.readTimeout = 10000
            connection.setRequestProperty("User-Agent", "PistonPort-Android")
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            val body = connection.inputStream.use { it.readNBytes(responseLimit + 1) }
            if (body.size > responseLimit) return null
            return findUpdate(JSONObject(body.decodeToString()), currentVersion)
        } finally {
            connection.disconnect()
        }
    }

    private fun version(tag: String): List<Int>? =
        versionTag.matchEntire(tag)?.groupValues?.drop(1)?.map(String::toInt)

    fun findUpdate(release: JSONObject, currentVersion: String): Update? {
        if (release.optBoolean("draft") || release.optBoolean("prerelease")) return null
        val tag = release.optString("tag_name")
        val offered = version(tag) ?: return null
        val installed = version(currentVersion) ?: return null
        val difference = offered.zip(installed).firstOrNull { (next, current) -> next != current }
        if (difference == null || difference.first < difference.second) return null

        val assets = release.optJSONArray("assets") ?: return null
        for (index in 0 until assets.length()) {
            val asset = assets.optJSONObject(index) ?: continue
            val name = asset.optString("name")
            val download = "$releasesPage/download/$tag/$name"
            if (apkName.matches(name) && asset.optString("state") == "uploaded" &&
                asset.optLong("size") > 0 && asset.optString("browser_download_url") == download) {
                return Update(offered.joinToString("."), download)
            }
        }
        return null
    }
}
