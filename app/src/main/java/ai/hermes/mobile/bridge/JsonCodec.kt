package ai.hermes.mobile.bridge

/**
 * Deterministic pure-Kotlin JSON serialization and field-extraction helpers.
 * Avoids `org.json` Android framework stubs so bridge schemas run identically in JVM tests.
 */
internal object JsonCodec {
    private const val CONTROL_CHAR_LIMIT = 0x20
    private const val HEX_RADIX = 16
    private const val UNICODE_PAD_LENGTH = 4

    fun parseObject(json: String): Map<String, Any?> {
        require(json.isNotBlank()) { "JSON payload must not be blank" }
        return JsonObjectReader(json).parseRootObject()
    }

    fun quote(value: String): String {
        val builder = StringBuilder(value.length + 2)
        builder.append('"')
        for (ch in value) {
            when (ch) {
                '"' -> builder.append("\\\"")
                '\\' -> builder.append("\\\\")
                '\b' -> builder.append("\\b")
                '\u000C' -> builder.append("\\f")
                '\n' -> builder.append("\\n")
                '\r' -> builder.append("\\r")
                '\t' -> builder.append("\\t")
                else -> {
                    if (ch.code < CONTROL_CHAR_LIMIT) {
                        builder.append("\\u")
                        builder.append(ch.code.toString(HEX_RADIX).padStart(UNICODE_PAD_LENGTH, '0'))
                    } else {
                        builder.append(ch)
                    }
                }
            }
        }
        builder.append('"')
        return builder.toString()
    }

    fun encodeActionResult(
        ok: Boolean,
        detailKey: String? = null,
        detailValue: Any? = null,
        error: String? = null,
    ): String {
        val fields = ArrayList<String>(3)
        fields.add("\"ok\":$ok")
        if (detailKey != null && detailValue != null) {
            val encodedValue =
                when (detailValue) {
                    is String -> quote(detailValue)
                    is Boolean, is Number -> detailValue.toString()
                    else -> quote(detailValue.toString())
                }
            fields.add("${quote(detailKey)}:$encodedValue")
        }
        if (error != null) {
            fields.add("\"error\":${quote(error)}")
        }
        return fields.joinToString(separator = ",", prefix = "{", postfix = "}")
    }

    fun requireString(
        map: Map<String, Any?>,
        key: String,
    ): String = (map[key] as? String) ?: throw IllegalArgumentException("Missing or invalid string field '$key'")

    fun requireBoolean(
        map: Map<String, Any?>,
        key: String,
    ): Boolean = (map[key] as? Boolean) ?: throw IllegalArgumentException("Missing or invalid boolean field '$key'")

    fun requireInt(
        map: Map<String, Any?>,
        key: String,
    ): Int =
        (map[key] as? Number)?.toInt()
            ?: throw IllegalArgumentException("Missing or invalid integer field '$key'")

    fun requireLong(
        map: Map<String, Any?>,
        key: String,
    ): Long =
        (map[key] as? Number)?.toLong()
            ?: throw IllegalArgumentException("Missing or invalid long field '$key'")

    fun requireFloat(
        map: Map<String, Any?>,
        key: String,
    ): Float =
        (map[key] as? Number)?.toFloat()
            ?: throw IllegalArgumentException("Missing or invalid float field '$key'")
}
