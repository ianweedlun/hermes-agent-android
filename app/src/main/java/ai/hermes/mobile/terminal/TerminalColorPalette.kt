package ai.hermes.mobile.terminal

import android.graphics.Color
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan

/**
 * Maps ANSI 16/256/RGB [TerminalColor] values to Android ARGB colors and renders
 * [TerminalBufferState] into a styled [SpannableStringBuilder].
 */
internal object TerminalColorPalette {
    private const val DIM_ALPHA = 160
    private const val CUBE_OFFSET = 16
    private const val CUBE_SIZE = 216
    private const val GRAYSCALE_OFFSET = 232
    private const val COLOR_COMPONENT_MAX = 255

    private val standardColors =
        intArrayOf(
            Color.rgb(0, 0, 0),
            Color.rgb(205, 49, 49),
            Color.rgb(13, 188, 121),
            Color.rgb(229, 229, 16),
            Color.rgb(36, 114, 200),
            Color.rgb(188, 63, 188),
            Color.rgb(17, 168, 205),
            Color.rgb(229, 229, 229),
        )

    private val brightColors =
        intArrayOf(
            Color.rgb(102, 102, 102),
            Color.rgb(241, 76, 76),
            Color.rgb(35, 209, 139),
            Color.rgb(245, 245, 67),
            Color.rgb(59, 142, 234),
            Color.rgb(214, 112, 214),
            Color.rgb(41, 184, 219),
            Color.rgb(255, 255, 255),
        )

    fun renderBufferToSpannable(
        buffer: TerminalBufferState,
        defaultForeground: Int,
        defaultBackground: Int,
    ): SpannableStringBuilder {
        val builder = SpannableStringBuilder()
        buffer.lines.forEachIndexed { lineIndex, line ->
            if (lineIndex > 0) {
                builder.append('\n')
            }
            for (span in line.spans) {
                appendStyledSpan(builder, span, defaultForeground, defaultBackground)
            }
        }
        return builder
    }

    private fun appendStyledSpan(
        builder: SpannableStringBuilder,
        span: TerminalSpan,
        defaultForeground: Int,
        defaultBackground: Int,
    ) {
        if (span.text.isEmpty()) {
            return
        }
        val start = builder.length
        builder.append(span.text)
        val end = builder.length
        val style = span.style

        var fg = resolveColor(style.foreground)
        var bg = resolveColor(style.background)
        if (style.inverse) {
            val resolvedFg = fg ?: defaultForeground
            val resolvedBg = bg ?: defaultBackground
            fg = resolvedBg
            bg = resolvedFg
        }
        if (style.dim) {
            val base = fg ?: defaultForeground
            fg = Color.argb(DIM_ALPHA, Color.red(base), Color.green(base), Color.blue(base))
        }
        fg?.let { builder.setSpan(ForegroundColorSpan(it), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE) }
        bg?.let { builder.setSpan(BackgroundColorSpan(it), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE) }
        applyTypefaceSpans(builder, style, start, end)
    }

    private fun applyTypefaceSpans(
        builder: SpannableStringBuilder,
        style: AnsiTextStyle,
        start: Int,
        end: Int,
    ) {
        val typefaceStyle =
            when {
                style.bold && style.italic -> Typeface.BOLD_ITALIC
                style.bold -> Typeface.BOLD
                style.italic -> Typeface.ITALIC
                else -> Typeface.NORMAL
            }
        if (typefaceStyle != Typeface.NORMAL) {
            builder.setSpan(StyleSpan(typefaceStyle), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (style.underline) {
            builder.setSpan(UnderlineSpan(), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    fun resolveColor(color: TerminalColor): Int? =
        when (color) {
            TerminalColor.Default -> null
            is TerminalColor.Standard -> standardColors.getOrNull(color.index)
            is TerminalColor.Bright -> brightColors.getOrNull(color.index)
            is TerminalColor.Indexed256 -> resolveIndexed256(color.index)
            is TerminalColor.Rgb -> Color.rgb(color.red, color.green, color.blue)
        }

    private fun resolveIndexed256(index: Int): Int? {
        val clamped = index.coerceIn(0, COLOR_COMPONENT_MAX)
        return when {
            clamped < 8 -> standardColors[clamped]
            clamped < CUBE_OFFSET -> brightColors[clamped - 8]
            clamped < CUBE_OFFSET + CUBE_SIZE -> {
                val n = clamped - CUBE_OFFSET
                val r = cubeComponent(n / 36)
                val g = cubeComponent((n % 36) / 6)
                val b = cubeComponent(n % 6)
                Color.rgb(r, g, b)
            }
            else -> {
                val gray = (clamped - GRAYSCALE_OFFSET) * 10 + 8
                Color.rgb(gray, gray, gray)
            }
        }
    }

    private fun cubeComponent(step: Int): Int = if (step == 0) 0 else step * 40 + 55
}
