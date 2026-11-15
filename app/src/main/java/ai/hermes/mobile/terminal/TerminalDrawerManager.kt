package ai.hermes.mobile.terminal

import ai.hermes.mobile.R
import ai.hermes.mobile.databinding.ActivityMainBinding
import android.content.res.Configuration
import android.os.Build
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Coordinates the Material 3 sliding terminal bottom sheet / expanded side drawer,
 * drag-handle and toolbar toggles, foldable posture transitions, and predictive back dismissal.
 */
class TerminalDrawerManager(
    private val activity: AppCompatActivity,
    private val binding: ActivityMainBinding,
    private val session: TerminalSession,
) {
    private val standardEasing = PathInterpolator(0.2f, 0f, 0f, 1f)
    private val _posture =
        MutableStateFlow(DrawerPosture.fromScreenWidthDp(activity.resources.configuration.screenWidthDp))
    val posture: StateFlow<DrawerPosture> = _posture.asStateFlow()

    private val _isExpanded = MutableStateFlow(false)
    val isExpanded: StateFlow<Boolean> = _isExpanded.asStateFlow()

    private lateinit var bottomSheetBehavior: BottomSheetBehavior<View>
    private var stateObserverJob: Job? = null

    private val backPressedCallback =
        object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                setExpanded(false)
            }
        }

    private val bottomSheetCallback =
        object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(
                bottomSheet: View,
                newState: Int,
            ) {
                if (_posture.value != DrawerPosture.COMPACT_BOTTOM_SHEET) {
                    return
                }
                when (newState) {
                    BottomSheetBehavior.STATE_EXPANDED -> {
                        TerminalUiHelpers.performHaptic(bottomSheet, confirm = true)
                        syncUiState(expanded = true)
                    }
                    BottomSheetBehavior.STATE_COLLAPSED -> syncUiState(expanded = false)
                    BottomSheetBehavior.STATE_DRAGGING -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            bottomSheet.performHapticFeedback(HapticFeedbackConstants.GESTURE_START)
                        }
                    }
                    else -> Unit
                }
            }

            override fun onSlide(
                bottomSheet: View,
                slideOffset: Float,
            ) = Unit
        }

    fun attach(scope: CoroutineScope) {
        bottomSheetBehavior = BottomSheetBehavior.from(binding.terminalBottomSheet)
        bottomSheetBehavior.addBottomSheetCallback(bottomSheetCallback)
        activity.onBackPressedDispatcher.addCallback(activity, backPressedCallback)

        binding.terminalView.bindSession(session, scope)
        binding.terminalDragHandle.setOnClickListener { setExpanded(!_isExpanded.value) }
        binding.btnToggleTerminal.setOnClickListener { setExpanded(!_isExpanded.value) }
        binding.btnClearTerminal.setOnClickListener {
            TerminalUiHelpers.performHaptic(binding.btnClearTerminal, confirm = true)
            binding.terminalView.clearOutput()
        }

        stateObserverJob?.cancel()
        stateObserverJob =
            scope.launch(Dispatchers.Main.immediate) {
                session.outputBuffer.collectLatest { bufferState ->
                    binding.terminalOverflowBadge.visibility =
                        if (bufferState.overflowDetected) View.VISIBLE else View.GONE
                }
            }

        applyPosture(_posture.value)
        binding.coordinatorLayout.post { applyPosture(_posture.value) }
    }

    fun detach() {
        stateObserverJob?.cancel()
        stateObserverJob = null
        if (::bottomSheetBehavior.isInitialized) {
            bottomSheetBehavior.removeBottomSheetCallback(bottomSheetCallback)
        }
        backPressedCallback.remove()
    }

    fun setExpanded(expanded: Boolean) {
        TerminalUiHelpers.performHaptic(binding.terminalBottomSheet, confirm = true)
        if (_posture.value == DrawerPosture.COMPACT_BOTTOM_SHEET) {
            bottomSheetBehavior.state =
                if (expanded) BottomSheetBehavior.STATE_EXPANDED else BottomSheetBehavior.STATE_COLLAPSED
            syncUiState(expanded)
        } else {
            val widthPx =
                DrawerPosture.computeSideDrawerWidthPx(
                    containerWidthPx =
                        binding.coordinatorLayout.width.takeIf { it > 0 }
                            ?: activity.resources.displayMetrics.widthPixels,
                    density = activity.resources.displayMetrics.density,
                )
            animateSideDrawer(open = expanded, drawerWidthPx = widthPx)
            syncUiState(expanded)
        }
    }

    fun onConfigurationChanged(newConfig: Configuration) {
        val nextPosture = DrawerPosture.fromScreenWidthDp(newConfig.screenWidthDp)
        _posture.value = nextPosture
        binding.coordinatorLayout.post { applyPosture(nextPosture) }
    }

    private fun applyPosture(posture: DrawerPosture) {
        val params = binding.terminalBottomSheet.layoutParams as CoordinatorLayout.LayoutParams
        val density = activity.resources.displayMetrics.density
        val totalHeight =
            binding.coordinatorLayout.height.takeIf { it > 0 }
                ?: activity.resources.displayMetrics.heightPixels
        val totalWidth =
            binding.coordinatorLayout.width.takeIf { it > 0 }
                ?: activity.resources.displayMetrics.widthPixels

        if (posture == DrawerPosture.COMPACT_BOTTOM_SHEET) {
            params.behavior = bottomSheetBehavior
            params.width = ViewGroup.LayoutParams.MATCH_PARENT
            params.height = (totalHeight * COMPACT_SHEET_HEIGHT_RATIO).roundToInt()
            params.gravity = Gravity.NO_GRAVITY
            params.topMargin = 0
            binding.terminalBottomSheet.layoutParams = params
            binding.terminalBottomSheet.translationX = 0f
            binding.terminalBottomSheet.visibility = View.VISIBLE
            binding.terminalBottomSheet.setBackgroundResource(R.drawable.bottom_sheet_background)
            binding.terminalDragHandle.visibility = View.VISIBLE
            TerminalUiHelpers.updateEndMargin(binding.webViewContainer, 0)
            bottomSheetBehavior.state =
                if (_isExpanded.value) BottomSheetBehavior.STATE_EXPANDED else BottomSheetBehavior.STATE_COLLAPSED
        } else {
            val drawerWidth = DrawerPosture.computeSideDrawerWidthPx(totalWidth, density)
            params.behavior = null
            params.width = drawerWidth
            params.height = ViewGroup.LayoutParams.MATCH_PARENT
            params.gravity = Gravity.END
            params.topMargin = binding.appBarLayout.height
            binding.terminalBottomSheet.layoutParams = params
            binding.terminalBottomSheet.setBackgroundResource(R.drawable.side_sheet_background)
            binding.terminalDragHandle.visibility = View.GONE
            val expanded = _isExpanded.value
            binding.terminalBottomSheet.visibility = if (expanded) View.VISIBLE else View.INVISIBLE
            binding.terminalBottomSheet.translationX = if (expanded) 0f else drawerWidth.toFloat()
            TerminalUiHelpers.updateEndMargin(binding.webViewContainer, if (expanded) drawerWidth else 0)
        }
    }

    private fun animateSideDrawer(
        open: Boolean,
        drawerWidthPx: Int,
    ) {
        val sheet = binding.terminalBottomSheet
        sheet.animate().cancel()
        if (open) {
            sheet.visibility = View.VISIBLE
            TerminalUiHelpers.updateEndMargin(binding.webViewContainer, drawerWidthPx)
            sheet
                .animate()
                .translationX(0f)
                .setDuration(MOTION_DURATION_MEDIUM_2_MS)
                .setInterpolator(standardEasing)
                .withEndAction { binding.terminalView.focusInput() }
                .start()
        } else {
            TerminalUiHelpers.updateEndMargin(binding.webViewContainer, 0)
            sheet
                .animate()
                .translationX(drawerWidthPx.toFloat())
                .setDuration(MOTION_DURATION_MEDIUM_2_MS)
                .setInterpolator(standardEasing)
                .withEndAction { sheet.visibility = View.INVISIBLE }
                .start()
        }
    }

    private fun syncUiState(expanded: Boolean) {
        _isExpanded.value = expanded
        backPressedCallback.isEnabled = expanded
        binding.terminalSheetHint.setText(
            if (expanded) R.string.terminal_sheet_hint_collapse else R.string.terminal_sheet_hint,
        )
    }

    companion object {
        private const val COMPACT_SHEET_HEIGHT_RATIO = 0.72f
        private const val MOTION_DURATION_MEDIUM_2_MS = 300L
    }
}
