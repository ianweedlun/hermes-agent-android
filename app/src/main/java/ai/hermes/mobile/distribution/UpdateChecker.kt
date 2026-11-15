package ai.hermes.mobile.distribution

import android.util.Log
import org.json.JSONException
import org.json.JSONObject

data class ReleaseInfo(
    val tagName: String,
    val htmlUrl: String,
    val apkDownloadUrl: String?,
    val isNewer: Boolean,
)

/**
 * Validates releases and compares semantic versioning per DISTRIBUTION-CONTRACT.md.
 */
object UpdateChecker {
    fun parseReleaseJson(
        rawJson: String,
        currentVersion: String,
    ): ReleaseInfo? =
        try {
            val json = JSONObject(rawJson)
            val tagName = json.optString("tag_name", "")
            val htmlUrl = json.optString("html_url", "")

            var apkUrl: String? = null
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk")) {
                        apkUrl =
                            if (asset.has("browser_download_url")) {
                                asset.getString("browser_download_url")
                            } else {
                                null
                            }
                        break
                    }
                }
            }

            val isNewer = isVersionNewer(tagName, currentVersion)
            ReleaseInfo(
                tagName = tagName,
                htmlUrl = htmlUrl,
                apkDownloadUrl = apkUrl,
                isNewer = isNewer,
            )
        } catch (e: JSONException) {
            Log.d("UpdateChecker", "Failed to parse GitHub release JSON", e)
            null
        }

    /**
     * Compares semantic versions formatted as [v]MAJOR.MINOR.PATCH.
     */
    fun isVersionNewer(
        remote: String,
        current: String,
    ): Boolean {
        val remoteClean = remote.removePrefix("v").trim()
        val currentClean = current.removePrefix("v").trim()

        val remoteParts = remoteClean.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = currentClean.split(".").mapNotNull { it.toIntOrNull() }

        if (remoteParts.isEmpty() || currentParts.isEmpty()) {
            return false
        }

        val length = maxOf(remoteParts.size, currentParts.size)
        for (i in 0 until length) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }
}
