package ai.hermes.mobile.bridge

/**
 * Power source classifications reported in `BatteryStatus.plugType` per `HARDWARE-BRIDGE-CONTRACT.md`.
 */
enum class BatteryPlugType(
    val wireName: String,
) {
    AC("ac"),
    USB("usb"),
    WIRELESS("wireless"),
    UNKNOWN("unknown"),
    ;

    companion object {
        fun fromWireName(value: String?): BatteryPlugType {
            val normalized = value?.trim()?.lowercase()
            return entries.firstOrNull { it.wireName == normalized } ?: UNKNOWN
        }
    }
}

/**
 * Battery health states reported in `BatteryStatus.health` per `HARDWARE-BRIDGE-CONTRACT.md`.
 */
enum class BatteryHealth(
    val wireName: String,
) {
    GOOD("good"),
    OVERHEAT("overheat"),
    COLD("cold"),
    DEAD("dead"),
    UNKNOWN("unknown"),
    ;

    companion object {
        fun fromWireName(value: String?): BatteryHealth {
            val normalized = value?.trim()?.lowercase()
            return entries.firstOrNull { it.wireName == normalized } ?: UNKNOWN
        }
    }
}

/**
 * Immutable battery telemetry snapshot matching `getBatteryStatus` in `HARDWARE-BRIDGE-CONTRACT.md`.
 */
data class BatteryStatus(
    val percentage: Int,
    val isCharging: Boolean,
    val plugType: BatteryPlugType,
    val temperatureC: Float,
    val health: BatteryHealth,
) {
    fun toJson(): String {
        val safeTemp = if (temperatureC.isFinite()) temperatureC else 0.0f
        return buildString {
            append('{')
            append("\"percentage\":$percentage,")
            append("\"isCharging\":$isCharging,")
            append("\"plugType\":${JsonCodec.quote(plugType.wireName)},")
            append("\"temperatureC\":$safeTemp,")
            append("\"health\":${JsonCodec.quote(health.wireName)}")
            append('}')
        }
    }

    companion object {
        fun fromJson(json: String): BatteryStatus {
            val map = JsonCodec.parseObject(json)
            return BatteryStatus(
                percentage = JsonCodec.requireInt(map, "percentage").coerceIn(0, 100),
                isCharging = JsonCodec.requireBoolean(map, "isCharging"),
                plugType = BatteryPlugType.fromWireName(JsonCodec.requireString(map, "plugType")),
                temperatureC = JsonCodec.requireFloat(map, "temperatureC"),
                health = BatteryHealth.fromWireName(JsonCodec.requireString(map, "health")),
            )
        }
    }
}
