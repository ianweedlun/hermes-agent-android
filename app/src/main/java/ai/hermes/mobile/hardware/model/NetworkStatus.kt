package ai.hermes.mobile.hardware.model

import org.json.JSONObject

/**
 * Structured network connectivity telemetry model per HARDWARE-BRIDGE-CONTRACT.md.
 */
data class NetworkStatus(
    val isConnected: Boolean,
    val type: String,
    val isMetered: Boolean,
    val isOffline: Boolean,
) {
    fun toJson(): JSONObject =
        JSONObject().apply {
            put("isConnected", isConnected)
            put("type", type)
            put("isMetered", isMetered)
            put("isOffline", isOffline)
        }

    fun toJsonString(): String = toJson().toString()

    companion object {
        const val TYPE_WIFI = "wifi"
        const val TYPE_CELLULAR = "cellular"
        const val TYPE_ETHERNET = "ethernet"
        const val TYPE_OTHER = "other"
        const val TYPE_NONE = "none"

        fun offline(): NetworkStatus =
            NetworkStatus(
                isConnected = false,
                type = TYPE_NONE,
                isMetered = false,
                isOffline = true,
            )
    }
}
