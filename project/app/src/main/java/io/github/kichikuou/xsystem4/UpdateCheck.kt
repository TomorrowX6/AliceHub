package io.github.kichikuou.xsystem4

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object UpdateCheck {
    private const val API = "https://api.github.com/repos/TomorrowX6/AliceHub"
    private val releaseTag = Regex("[0-9a-fA-F]{7,40}")
    private val buildCommit = Regex("[0-9a-fA-F]{40}")

    fun releaseUrl(tag: String) = "https://github.com/TomorrowX6/AliceHub/releases/tag/$tag"

    fun compareUrl(commit: String, tag: String): String? =
        if (buildCommit.matches(commit) && releaseTag.matches(tag)) {
            "$API/compare/$tag...$commit?per_page=1"
        } else null

    fun isBehind(comparison: JSONObject): Boolean = comparison.optString("status") == "behind"

    private fun fetchJson(url: String): JSONObject? {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            connection.setRequestProperty("User-Agent", "AliceHub")
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            return connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
        } finally {
            connection.disconnect()
        }
    }

    fun fetchNewRelease(commit: String): String? = try {
        if (!buildCommit.matches(commit)) null else {
            val tag = fetchJson("$API/releases/latest")?.optString("tag_name").orEmpty()
            compareUrl(commit, tag)?.let { url ->
                val comparison = fetchJson(url)
                tag.takeIf { comparison != null && isBehind(comparison) }
            }
        }
    } catch (_: Exception) {
        null
    }
}
