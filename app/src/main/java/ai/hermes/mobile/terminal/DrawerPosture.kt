package ai.hermes.mobile.terminal

import kotlin.math.roundToInt

/**
 * Responsive container mode per DESIGN-GUIDELINES.md:
 * compact (<600dp) uses a modal/sliding bottom sheet; expanded (>=600dp) uses a dual-pane side drawer.
 */
enum class DrawerPosture {
    COMPACT_BOTTOM_SHEET,
    EXPANDED_SIDE_DRAWER,
    ;

    companion object {
        const val EXPANDED_WIDTH_DP = 600
        private const val MAX_SIDE_DRAWER_WIDTH_DP = 420
        private const val SIDE_DRAWER_WIDTH_FRACTION = 0.42f

        fun fromScreenWidthDp(screenWidthDp: Int): DrawerPosture =
            if (screenWidthDp >= EXPANDED_WIDTH_DP) {
                EXPANDED_SIDE_DRAWER
            } else {
                COMPACT_BOTTOM_SHEET
            }

        fun computeSideDrawerWidthPx(
            containerWidthPx: Int,
            density: Float,
        ): Int {
            val maxPx = (MAX_SIDE_DRAWER_WIDTH_DP * density).roundToInt()
            val fractionalPx = (containerWidthPx * SIDE_DRAWER_WIDTH_FRACTION).roundToInt()
            return fractionalPx.coerceAtMost(maxPx).coerceAtLeast(1)
        }
    }
}
