package ai.hermes.mobile.bridge

/**
 * Network transport types reported in `NetworkStatus.type` per `HARDWARE-BRIDGE-CONTRACT.md`.
 */
enum class NetworkType(
    val wireName: String,
) {
    WIFI("wifi"),
    CELLULAR("cellular"),
    ETHERNET("ethernet"),
    NONE("none"),
    ;

    companion object {
        fun fromWireName(value: String?): NetworkType {
            val normalized = value?.trim()?.lowercase()
            return entries.firstOrNull { it.wireName == normalized } ?: NONE
        }
    }
}

/**
 * Immutable connectivity telemetry snapshot matching `getNetworkStatus` in `HARDWARE-BRIDGE-CONTRACT.md`.
 */
data class NetworkStatus(
    val isConnected: Boolean,
    val type: NetworkType,
    val isMetered: Boolean,
    val isOffline: Boolean = !isConnected,
) {
    fun toJson(): String =
        buildString {
            append('{')
            append("\"isConnected\":$isConnected,")
            append("\"type\":${JsonCodec.quote(type.wireName)},")
            append("\"isMetered\":$isMetered,")
            append("\"isOffline\":$isOffline")
            append('}')
        }

    companion object {
        val OFFLINE: NetworkStatus =
            NetworkStatus(
                isConnected = false,
                type = NetworkType.NONE,
                isMetered = false,
                isOffline = true,
            )

        fun fromJson(json: String): NetworkStatus {
            val map = JsonCodec.parseObject(json)
            val connected = JsonCodec.requireBoolean(map, "isConnected")
            val offline = (map["isOffline"] as? Boolean) ?: !connected
            return NetworkStatus(
                isConnected = connected,
                type = NetworkType.fromWireName(JsonCodec.requireString(map, "type")),
                isMetered = JsonCodec.requireBoolean(map, "isMetered"),
                isOffline = offline,
            )
        }
    }
}
