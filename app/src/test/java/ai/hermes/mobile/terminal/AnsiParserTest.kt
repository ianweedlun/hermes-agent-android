package ai.hermes.mobile.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnsiParserTest {
    @Test
    fun parsesSgrColorsAndStylesAcrossSplitChunks() {
        val parser = AnsiParser()

        val firstTokens = parser.feed("plain \u001B[1;3")
        val secondTokens = parser.feed("2mgreen\u001B[0m done")
        val allTokens = firstTokens + secondTokens

        val textTokens = allTokens.filterIsInstance<AnsiToken.Text>()
        assertEquals(3, textTokens.size)
        assertEquals("plain ", textTokens[0].value)
        assertEquals(TerminalColor.Default, textTokens[0].style.foreground)

        assertEquals("green", textTokens[1].value)
        assertTrue(textTokens[1].style.bold)
        assertEquals(TerminalColor.Standard(2), textTokens[1].style.foreground)

        assertEquals(" done", textTokens[2].value)
        assertEquals(AnsiTextStyle(), textTokens[2].style)
    }

    @Test
    fun parsesExtended256AndTruecolorAndFiltersOsc() {
        val parser = AnsiParser()

        val tokens =
            parser.feed(
                "\u001B]0;Hermes Shell\u0007\u001B[38;5;208morange\u001B[48;2;10;20;30m rgb",
            )

        val textTokens = tokens.filterIsInstance<AnsiToken.Text>()
        assertEquals(2, textTokens.size)
        assertEquals("orange", textTokens[0].value)
        assertEquals(TerminalColor.Indexed256(208), textTokens[0].style.foreground)

        assertEquals(" rgb", textTokens[1].value)
        assertEquals(TerminalColor.Rgb(10, 20, 30), textTokens[1].style.background)
    }
}
