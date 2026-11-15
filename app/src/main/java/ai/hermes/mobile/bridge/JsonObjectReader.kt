package ai.hermes.mobile.bridge

/**
 * Lightweight, zero-dependency recursive-descent JSON parser supporting objects, arrays,
 * strings (with standard and unicode escapes), numbers, booleans, and null.
 */
internal class JsonObjectReader(
    private val input: String,
) {
    private var index: Int = 0

    private val currentChar: Char?
        get() = if (index < input.length) input[index] else null

    fun parseRootObject(): Map<String, Any?> {
        val parsed = readObject()
        skipWhitespace()
        require(index == input.length) { "Unexpected trailing JSON content at index $index" }
        return parsed
    }

    private fun readObject(): Map<String, Any?> {
        skipWhitespace()
        consume('{')
        skipWhitespace()
        val map = LinkedHashMap<String, Any?>()
        if (currentChar == '}') {
            index += 1
        } else {
            while (true) {
                skipWhitespace()
                val key = readString()
                skipWhitespace()
                consume(':')
                map[key] = readValue()
                skipWhitespace()
                val delimiter = currentChar ?: throw IllegalArgumentException("Unterminated JSON object")
                if (delimiter == ',') {
                    index += 1
                } else if (delimiter == '}') {
                    index += 1
                    break
                } else {
                    throw IllegalArgumentException("Expected ',' or '}' at index $index, got '$delimiter'")
                }
            }
        }
        return map
    }

    private fun readArray(): List<Any?> {
        consume('[')
        skipWhitespace()
        val list = ArrayList<Any?>()
        if (currentChar == ']') {
            index += 1
        } else {
            while (true) {
                list.add(readValue())
                skipWhitespace()
                val delimiter = currentChar ?: throw IllegalArgumentException("Unterminated JSON array")
                if (delimiter == ',') {
                    index += 1
                } else if (delimiter == ']') {
                    index += 1
                    break
                } else {
                    throw IllegalArgumentException("Expected ',' or ']' at index $index, got '$delimiter'")
                }
            }
        }
        return list
    }

    private fun readValue(): Any? {
        skipWhitespace()
        val ch = currentChar ?: throw IllegalArgumentException("Unexpected end of JSON input")
        return when (ch) {
            '"' -> readString()
            '{' -> readObject()
            '[' -> readArray()
            't', 'f', 'n' -> readLiteral()
            '-', in '0'..'9' -> readNumber()
            else -> throw IllegalArgumentException("Unexpected JSON token '$ch' at index $index")
        }
    }

    private fun readString(): String {
        consume('"')
        val builder = StringBuilder()
        while (index < input.length) {
            val ch = input[index]
            index += 1
            if (ch == '"') {
                return builder.toString()
            }
            if (ch == '\\') {
                builder.append(readEscapeChar())
            } else {
                require(ch.code >= MIN_UNESCAPED_CHAR_CODE) {
                    "Unescaped control character in JSON string at index ${index - 1}"
                }
                builder.append(ch)
            }
        }
        throw IllegalArgumentException("Unterminated JSON string")
    }

    private fun readEscapeChar(): Char {
        if (index >= input.length) {
            throw IllegalArgumentException("Unterminated JSON escape sequence")
        }
        val escaped = input[index]
        index += 1
        return when (escaped) {
            '"', '\\', '/' -> escaped
            'b' -> '\b'
            'f' -> '\u000C'
            'n' -> '\n'
            'r' -> '\r'
            't' -> '\t'
            'u' -> {
                require(index + UNICODE_HEX_LENGTH <= input.length) { "Incomplete unicode escape at index $index" }
                val hex = input.substring(index, index + UNICODE_HEX_LENGTH)
                index += UNICODE_HEX_LENGTH
                try {
                    hex.toInt(HEX_RADIX).toChar()
                } catch (e: NumberFormatException) {
                    throw IllegalArgumentException("Invalid unicode escape sequence: $hex", e)
                }
            }
            else -> throw IllegalArgumentException("Invalid JSON escape '$escaped' at index ${index - 1}")
        }
    }

    private fun readNumber(): Number {
        val start = index
        if (currentChar == '-') {
            index += 1
        }
        while (index < input.length && input[index].isDigit()) {
            index += 1
        }
        var isFloating = false
        if (currentChar == '.') {
            isFloating = true
            index += 1
            while (index < input.length && input[index].isDigit()) {
                index += 1
            }
        }
        if (currentChar == 'e' || currentChar == 'E') {
            isFloating = true
            index += 1
            if (currentChar == '+' || currentChar == '-') {
                index += 1
            }
            while (index < input.length && input[index].isDigit()) {
                index += 1
            }
        }
        val token = input.substring(start, index)
        return try {
            if (isFloating) token.toDouble() else token.toLong()
        } catch (e: NumberFormatException) {
            throw IllegalArgumentException("Invalid JSON number '$token'", e)
        }
    }

    private fun readLiteral(): Any? =
        when {
            input.startsWith("true", startIndex = index) -> true.also { index += TRUE_LENGTH }
            input.startsWith("false", startIndex = index) -> false.also { index += FALSE_LENGTH }
            input.startsWith("null", startIndex = index) -> null.also { index += NULL_LENGTH }
            else -> throw IllegalArgumentException("Invalid JSON literal at index $index")
        }

    private fun consume(expected: Char) {
        val actual = currentChar
        require(actual == expected) { "Expected '$expected' at index $index, got '$actual'" }
        index += 1
    }

    private fun skipWhitespace() {
        while (index < input.length && input[index].isWhitespace()) {
            index += 1
        }
    }

    companion object {
        private const val MIN_UNESCAPED_CHAR_CODE = 0x20
        private const val UNICODE_HEX_LENGTH = 4
        private const val HEX_RADIX = 16
        private const val TRUE_LENGTH = 4
        private const val FALSE_LENGTH = 5
        private const val NULL_LENGTH = 4
    }
}
