package ai.hermes.mobile.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawerPostureTest {
    @Test
    fun selectsCompactBottomSheetBelow600dpAndSideDrawerAtOrAbove600dp() {
        assertEquals(DrawerPosture.COMPACT_BOTTOM_SHEET, DrawerPosture.fromScreenWidthDp(360))
        assertEquals(DrawerPosture.COMPACT_BOTTOM_SHEET, DrawerPosture.fromScreenWidthDp(599))
        assertEquals(DrawerPosture.EXPANDED_SIDE_DRAWER, DrawerPosture.fromScreenWidthDp(600))
        assertEquals(DrawerPosture.EXPANDED_SIDE_DRAWER, DrawerPosture.fromScreenWidthDp(840))
    }

    @Test
    fun sideDrawerWidthRespectsMaxCapAndAvoidsCrossingCenterHinge() {
        // 2400px at 3.0 density = 800dp -> 42% is 336dp (1008px), < 50% center hinge
        val widthFoldable = DrawerPosture.computeSideDrawerWidthPx(containerWidthPx = 2400, density = 3.0f)
        assertTrue(widthFoldable < 1200)

        // Very wide tablet: 3600px at 2.0 density = 1800dp -> capped at 420dp (840px)
        val widthTablet = DrawerPosture.computeSideDrawerWidthPx(containerWidthPx = 3600, density = 2.0f)
        assertEquals(840, widthTablet)
    }
}
