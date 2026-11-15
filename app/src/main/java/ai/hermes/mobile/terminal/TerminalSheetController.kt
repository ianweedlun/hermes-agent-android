package ai.hermes.mobile.terminal

import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.OnBackPressedDispatcher
import com.google.android.material.bottomsheet.BottomSheetBehavior

/**
 * Coordinates native sliding terminal bottom sheet behavior, haptic snaps,
 * and predictive back navigation per DESIGN-GUIDELINES.md.
 */
class TerminalSheetController(
    private val bottomSheetView: View,
    private val behavior: BottomSheetBehavior<View>,
    private val miniConsoleView: TextView? = null,
    private val terminalBuffer: TerminalBuffer = TerminalBuffer(),
    onBackPressedDispatcher: OnBackPressedDispatcher? = null,
) {
    val buffer: TerminalBuffer
        get() = terminalBuffer

    val backPressedCallback =
        object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                if (isExpanded()) {
                    collapse()
                }
            }
        }

    init {
        onBackPressedDispatcher?.addCallback(backPressedCallback)

        behavior.addBottomSheetCallback(
            object : BottomSheetBehavior.BottomSheetCallback() {
                override fun onStateChanged(
                    bottomSheet: View,
                    newState: Int,
                ) {
                    when (newState) {
                        BottomSheetBehavior.STATE_EXPANDED -> {
                            backPressedCallback.isEnabled = true
                            bottomSheetView.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                        }
                        BottomSheetBehavior.STATE_COLLAPSED -> {
                            backPressedCallback.isEnabled = false
                            bottomSheetView.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                        }
                        BottomSheetBehavior.STATE_HIDDEN -> {
                            backPressedCallback.isEnabled = false
                        }
                        else -> Unit
                    }
                }

                override fun onSlide(
                    bottomSheet: View,
                    slideOffset: Float,
                ) {
                    // Smooth transition updates if needed
                }
            },
        )
    }

    fun isExpanded(): Boolean =
        behavior.state == BottomSheetBehavior.STATE_EXPANDED ||
            behavior.state == BottomSheetBehavior.STATE_HALF_EXPANDED

    fun expand() {
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    fun collapse() {
        behavior.state = BottomSheetBehavior.STATE_COLLAPSED
    }

    fun toggle() {
        if (isExpanded()) {
            collapse()
        } else {
            expand()
        }
    }

    fun appendOutput(text: String) {
        terminalBuffer.appendString(text)
        miniConsoleView?.let { textView ->
            textView.post {
                textView.text = terminalBuffer.getLatestLine()
            }
        }
    }
}
