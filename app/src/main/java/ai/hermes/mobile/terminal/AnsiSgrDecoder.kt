package ai.hermes.mobile.terminal

/**
 * Decodes ANSI Select Graphic Rendition (SGR) parameter sequences into [AnsiTextStyle] updates.
 */
internal object AnsiSgrDecoder {
    private const val SGR_FG_BASE = 30
    private const val SGR_FG_MAX = 37
    private const val SGR_EXTENDED_FG = 38
    private const val SGR_FG_DEFAULT = 39
    private const val SGR_BG_BASE = 40
    private const val SGR_BG_MAX = 47
    private const val SGR_EXTENDED_BG = 48
    private const val SGR_BG_DEFAULT = 49
    private const val SGR_BRIGHT_FG_BASE = 90
    private const val SGR_BRIGHT_FG_MAX = 97
    private const val SGR_BRIGHT_BG_BASE = 100
    private const val SGR_BRIGHT_BG_MAX = 107
    private const val COLOR_MODE_256 = 5
    private const val COLOR_MODE_RGB = 2
    private const val MAX_COLOR_COMPONENT = 255

    fun applySgrSequence(
        initial: AnsiTextStyle,
        codes: List<Int>,
    ): AnsiTextStyle {
        var style = initial
        var index = 0
        while (index < codes.size) {
            val code = codes[index]
            if (code == SGR_EXTENDED_FG || code == SGR_EXTENDED_BG) {
                val (updated, consumed) =
                    applyExtendedColor(style, codes, index, isForeground = code == SGR_EXTENDED_FG)
                style = updated
                index += consumed
            } else {
                style = applyStandardSgr(style, code)
                index += 1
            }
        }
        return style
    }

    private fun applyExtendedColor(
        current: AnsiTextStyle,
        codes: List<Int>,
        index: Int,
        isForeground: Boolean,
    ): Pair<AnsiTextStyle, Int> {
        val mode = codes.getOrNull(index + 1)
        val colorAndConsumed =
            when (mode) {
                COLOR_MODE_256 -> {
                    val idx = codes.getOrNull(index + 2)?.coerceIn(0, MAX_COLOR_COMPONENT)
                    if (idx != null) TerminalColor.Indexed256(idx) to 3 else null
                }
                COLOR_MODE_RGB -> {
                    val r = codes.getOrNull(index + 2)?.coerceIn(0, MAX_COLOR_COMPONENT)
                    val g = codes.getOrNull(index + 3)?.coerceIn(0, MAX_COLOR_COMPONENT)
                    val b = codes.getOrNull(index + 4)?.coerceIn(0, MAX_COLOR_COMPONENT)
                    if (r != null && g != null && b != null) {
                        TerminalColor.Rgb(r, g, b) to 5
                    } else {
                        null
                    }
                }
                else -> null
            }
        return if (colorAndConsumed != null) {
            val (color, consumed) = colorAndConsumed
            val updated =
                if (isForeground) {
                    current.copy(foreground = color)
                } else {
                    current.copy(background = color)
                }
            updated to consumed
        } else {
            current to 1
        }
    }

    private fun applyStandardSgr(
        current: AnsiTextStyle,
        code: Int,
    ): AnsiTextStyle =
        when (code) {
            0 -> AnsiTextStyle()
            1 -> current.copy(bold = true)
            2 -> current.copy(dim = true)
            3 -> current.copy(italic = true)
            4 -> current.copy(underline = true)
            7 -> current.copy(inverse = true)
            21, 22 -> current.copy(bold = false, dim = false)
            23 -> current.copy(italic = false)
            24 -> current.copy(underline = false)
            27 -> current.copy(inverse = false)
            in SGR_FG_BASE..SGR_FG_MAX ->
                current.copy(foreground = TerminalColor.Standard(code - SGR_FG_BASE))
            SGR_FG_DEFAULT -> current.copy(foreground = TerminalColor.Default)
            in SGR_BG_BASE..SGR_BG_MAX ->
                current.copy(background = TerminalColor.Standard(code - SGR_BG_BASE))
            SGR_BG_DEFAULT -> current.copy(background = TerminalColor.Default)
            in SGR_BRIGHT_FG_BASE..SGR_BRIGHT_FG_MAX ->
                current.copy(foreground = TerminalColor.Bright(code - SGR_BRIGHT_FG_BASE))
            in SGR_BRIGHT_BG_BASE..SGR_BRIGHT_BG_MAX ->
                current.copy(background = TerminalColor.Bright(code - SGR_BRIGHT_BG_BASE))
            else -> current
        }
}
