package ai.hermes.mobile.bridge

/**
 * Immutable clipboard payload exposed over the hardware bridge.
 * Redacts [text] in [toString] when [isSensitive] is true to avoid accidental log leakage.
 */
data class ClipboardPayload(
    val text: String,
    val isSensitive: Boolean,
    val timestamp: Long,
) {
    override fun toString(): String =
        if (isSensitive) {
            "ClipboardPayload(text=***REDACTED***, isSensitive=true, timestamp=$timestamp)"
        } else {
            "ClipboardPayload(text=$text, isSensitive=false, timestamp=$timestamp)"
        }

    fun toJson(): String =
        buildString {
            append('{')
            append("\"text\":${JsonCodec.quote(text)},")
            append("\"isSensitive\":$isSensitive,")
            append("\"timestamp\":$timestamp")
            append('}')
        }

    companion object {
        fun fromJson(json: String): ClipboardPayload {
            val map = JsonCodec.parseObject(json)
            return ClipboardPayload(
                text = JsonCodec.requireString(map, "text"),
                isSensitive = JsonCodec.requireBoolean(map, "isSensitive"),
                timestamp = JsonCodec.requireLong(map, "timestamp"),
            )
        }
    }
}
