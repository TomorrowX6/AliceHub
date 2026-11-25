package io.github.kichikuou.xsystem4

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object UpdateCheck {
    private const val LATEST_RELEASE = "https://api.github.com/repos/TomorrowX6/AliceHub/releases/latest"
    private val releaseTag = Regex("[0-9a-fA-F]{7,40}")
    private val buildCommit = Regex("[0-9a-fA-F]{40}")

    fun releaseUrl(tag: String) = "https://github.com/TomorrowX6/AliceHub/releases/tag/$tag"

    fun newReleaseTag(commit: String, tag: String): String? =
        tag.takeIf {
            buildCommit.matches(commit) && releaseTag.matches(it) && !commit.startsWith(it, ignoreCase = true)
        }

    fun fetchNewRelease(commit: String): String? {
        if (!buildCommit.matches(commit)) return null
        return try {
            val connection = URL(LATEST_RELEASE).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 5_000
                connection.readTimeout = 5_000
                connection.setRequestProperty("User-Agent", "AliceHub")
                connection.setRequestProperty("Accept", "application/vnd.github+json")
                if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
                val tag = connection.inputStream.bufferedReader().use {
                    JSONObject(it.readText()).optString("tag_name")
                }
                newReleaseTag(commit, tag)
            } finally {
                connection.disconnect()
            }
        } catch (_: Exception) {
            null
        }
    }
}
