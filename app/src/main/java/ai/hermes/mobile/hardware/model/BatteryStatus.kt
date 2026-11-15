package ai.hermes.mobile.hardware.model

import org.json.JSONObject

/**
 * Structured battery and charging telemetry model per HARDWARE-BRIDGE-CONTRACT.md.
 */
data class BatteryStatus(
    val percentage: Int,
    val isCharging: Boolean,
    val plugType: String,
    val temperatureC: Float,
    val health: String,
) {
    fun toJson(): JSONObject =
        JSONObject().apply {
            put("percentage", percentage)
            put("isCharging", isCharging)
            put("plugType", plugType)
            put("temperatureC", temperatureC.toDouble())
            put("health", health)
        }

    fun toJsonString(): String = toJson().toString()

    companion object {
        const val PLUG_AC = "ac"
        const val PLUG_USB = "usb"
        const val PLUG_WIRELESS = "wireless"
        const val PLUG_NONE = "none"

        const val HEALTH_GOOD = "good"
        const val HEALTH_OVERHEAT = "overheat"
        const val HEALTH_UNKNOWN = "unknown"

        fun unknown(): BatteryStatus =
            BatteryStatus(
                percentage = -1,
                isCharging = false,
                plugType = PLUG_NONE,
                temperatureC = 0.0f,
                health = HEALTH_UNKNOWN,
            )
    }
}
