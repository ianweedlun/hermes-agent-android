package ai.hermes.mobile.distribution

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {
    @Test
    fun versionComparisonDetectsNewerReleases() {
        assertTrue(UpdateChecker.isVersionNewer("v0.2.0", "v0.1.0"))
        assertTrue(UpdateChecker.isVersionNewer("0.1.1", "0.1.0"))
        assertTrue(UpdateChecker.isVersionNewer("v1.0.0", "v0.9.9"))

        assertFalse(UpdateChecker.isVersionNewer("v0.1.0", "v0.1.0"))
        assertFalse(UpdateChecker.isVersionNewer("v0.0.9", "v0.1.0"))
        assertFalse(UpdateChecker.isVersionNewer("invalid", "v0.1.0"))
    }

    @Test
    fun parseReleaseJsonExtractsApkAndMetadata() {
        val json =
            """
            {
              "tag_name": "v0.2.0",
              "html_url": "https://github.com/ianweedlun/hermes-agent-android/releases/tag/v0.2.0",
              "assets": [
                {
                  "name": "checksums.sha256",
                  "browser_download_url": "https://github.com/checksums"
                },
                {
                  "name": "hermes-agent-v0.2.0-arm64-v8a.apk",
                  "browser_download_url": "https://github.com/hermes-agent.apk"
                }
              ]
            }
            """.trimIndent()

        val release = UpdateChecker.parseReleaseJson(json, "v0.1.0")
        assertNotNull(release)
        assertEquals("v0.2.0", release?.tagName)
        assertEquals("https://github.com/hermes-agent.apk", release?.apkDownloadUrl)
        assertTrue(release?.isNewer == true)
    }
}
