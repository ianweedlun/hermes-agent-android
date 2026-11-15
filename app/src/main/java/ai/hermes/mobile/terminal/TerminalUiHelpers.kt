package ai.hermes.mobile.terminal

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.View
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import kotlin.math.roundToInt

/**
 * Shared Material 3 UI helpers for terminal views and drawer controls.
 */
internal object TerminalUiHelpers {
    private const val MIN_TOUCH_TARGET_DP = 48
    private const val QUICK_KEY_PAD_DP = 10
    private const val SMALL_TEXT_SP = 11f

    fun resolveThemeColor(
        view: View,
        attrResId: Int,
    ): Int = MaterialColors.getColor(view, attrResId, Color.TRANSPARENT)

    fun dpToPx(
        view: View,
        dp: Int,
    ): Int = (dp * view.resources.displayMetrics.density).roundToInt()

    fun performHaptic(
        target: View,
        confirm: Boolean,
    ) {
        val flag =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (confirm) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.REJECT
            } else {
                HapticFeedbackConstants.KEYBOARD_TAP
            }
        target.performHapticFeedback(flag)
    }

    fun createQuickKeyButton(
        context: Context,
        label: String,
        descriptionResId: Int,
        onClick: () -> Unit,
    ): MaterialButton =
        MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = label
            minWidth = dpToPx(this, MIN_TOUCH_TARGET_DP)
            minHeight = dpToPx(this, MIN_TOUCH_TARGET_DP)
            val padH = dpToPx(this, QUICK_KEY_PAD_DP)
            setPadding(padH, 0, padH, 0)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, SMALL_TEXT_SP)
            typeface = Typeface.MONOSPACE
            contentDescription = context.getString(descriptionResId)
            setOnClickListener {
                performHaptic(this, confirm = true)
                onClick()
            }
        }

    fun updateEndMargin(
        target: View,
        marginEndPx: Int,
    ) {
        val params = target.layoutParams as? android.view.ViewGroup.MarginLayoutParams ?: return
        if (params.marginEnd != marginEndPx) {
            params.marginEnd = marginEndPx
            target.layoutParams = params
        }
    }
}
