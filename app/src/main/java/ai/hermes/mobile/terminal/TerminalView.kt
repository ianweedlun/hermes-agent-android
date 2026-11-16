package ai.hermes.mobile.terminal

import ai.hermes.mobile.R
import android.content.Context
import android.graphics.Typeface
import android.text.InputType
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Native Material 3 monospaced terminal console view supporting ANSI escape rendering,
 * auto-scrolling, 64KB buffer overflow notifications, grid resize calculation, and command input.
 */
class TerminalView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : LinearLayout(context, attrs, defStyleAttr) {
        private val overflowBanner = TextView(context)
        private val outputScrollView = ScrollView(context)
        private val outputTextView = TextView(context)
        private val commandInput = EditText(context)
        private val sendButton = ImageButton(context)

        private var boundSession: TerminalSession? = null
        private var observerJob: Job? = null
        private var autoScrollEnabled: Boolean = true

        init {
            orientation = VERTICAL
            configureOutputViews()
            configureInputBar()
        }

        fun bindSession(
            session: TerminalSession,
            scope: CoroutineScope,
        ) {
            observerJob?.cancel()
            boundSession = session
            updateGridDimensions(outputScrollView.width, outputScrollView.height)
            observerJob =
                scope.launch(Dispatchers.Main.immediate) {
                    session.outputBuffer.collectLatest { bufferState ->
                        renderBuffer(bufferState)
                    }
                }
        }

        fun clearOutput() {
            autoScrollEnabled = true
            boundSession?.clearBuffer()
            outputScrollView.scrollTo(0, 0)
        }

        fun focusInput() {
            commandInput.requestFocus()
        }

        override fun onSizeChanged(
            w: Int,
            h: Int,
            oldw: Int,
            oldh: Int,
        ) {
            super.onSizeChanged(w, h, oldw, oldh)
            post { updateGridDimensions(outputScrollView.width, outputScrollView.height) }
        }

        private fun configureOutputViews() {
            val padH = TerminalUiHelpers.dpToPx(this, BANNER_PAD_H_DP)
            val padV = TerminalUiHelpers.dpToPx(this, BANNER_PAD_V_DP)
            overflowBanner.apply {
                visibility = View.GONE
                setPadding(padH, padV, padH, padV)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, SMALL_TEXT_SP)
                typeface = Typeface.MONOSPACE
                setBackgroundColor(
                    TerminalUiHelpers.resolveThemeColor(this, com.google.android.material.R.attr.colorErrorContainer),
                )
                setTextColor(
                    TerminalUiHelpers.resolveThemeColor(this, com.google.android.material.R.attr.colorOnErrorContainer),
                )
                setOnClickListener { clearOutput() }
            }
            addView(overflowBanner, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

            val surfaceBg =
                TerminalUiHelpers.resolveThemeColor(this, com.google.android.material.R.attr.colorSurfaceContainerLow)
            val textFg = TerminalUiHelpers.resolveThemeColor(this, com.google.android.material.R.attr.colorOnSurface)
            val pad = TerminalUiHelpers.dpToPx(this, OUTPUT_PADDING_DP)
            outputTextView.apply {
                typeface = Typeface.MONOSPACE
                setTextSize(TypedValue.COMPLEX_UNIT_SP, TERMINAL_TEXT_SP)
                setTextColor(textFg)
                setTextIsSelectable(true)
                setPadding(pad, pad, pad, pad)
                text = context.getString(R.string.terminal_sheet_default_log)
            }
            outputScrollView.apply {
                isFillViewport = true
                setBackgroundColor(surfaceBg)
                addView(outputTextView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
                setOnScrollChangeListener { _, _, scrollY, _, _ ->
                    val bottom = scrollY + height
                    autoScrollEnabled = outputTextView.height - bottom <=
                        TerminalUiHelpers.dpToPx(
                            this,
                            AUTOSCROLL_THRESHOLD_DP,
                        )
                }
            }
            addView(outputScrollView, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        }

        private fun configureInputBar() {
            val bar =
                LinearLayout(context).apply {
                    orientation = HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, TerminalUiHelpers.dpToPx(this, INPUT_BAR_TOP_MARGIN_DP), 0, 0)
                }
            val spacing = TerminalUiHelpers.dpToPx(this, CONTROL_SPACING_DP)
            val touchSize = TerminalUiHelpers.dpToPx(this, MIN_TOUCH_TARGET_DP)
            val ctrlC =
                TerminalUiHelpers.createQuickKeyButton(context, "^C", R.string.terminal_action_ctrl_c) {
                    boundSession?.writeBytes(byteArrayOf(CTRL_C_BYTE))
                }
            val tab =
                TerminalUiHelpers.createQuickKeyButton(context, "Tab", R.string.terminal_action_tab) {
                    boundSession?.writeInput("\t")
                }
            commandInput.apply {
                hint = context.getString(R.string.terminal_input_hint)
                typeface = Typeface.MONOSPACE
                setTextSize(TypedValue.COMPLEX_UNIT_SP, INPUT_TEXT_SP)
                setTextColor(
                    TerminalUiHelpers.resolveThemeColor(this, com.google.android.material.R.attr.colorOnSurface),
                )
                setHintTextColor(
                    TerminalUiHelpers.resolveThemeColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant),
                )
                minHeight = touchSize
                imeOptions = EditorInfo.IME_ACTION_SEND
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                maxLines = 1
                setOnEditorActionListener { _, actionId, _ ->
                    if (actionId == EditorInfo.IME_ACTION_SEND || actionId == EditorInfo.IME_NULL) {
                        submitCurrentInput()
                        true
                    } else {
                        false
                    }
                }
            }
            val selectableAttr = TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, selectableAttr, true)
            sendButton.apply {
                setImageResource(R.drawable.ic_terminal_send)
                setBackgroundResource(selectableAttr.resourceId)
                setColorFilter(TerminalUiHelpers.resolveThemeColor(this, com.google.android.material.R.attr.colorPrimary))
                contentDescription = context.getString(R.string.terminal_action_send)
                setOnClickListener { submitCurrentInput() }
            }

            bar.addView(ctrlC, LayoutParams(LayoutParams.WRAP_CONTENT, touchSize).apply { marginEnd = spacing })
            bar.addView(tab, LayoutParams(LayoutParams.WRAP_CONTENT, touchSize).apply { marginEnd = spacing })
            bar.addView(commandInput, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = spacing })
            bar.addView(sendButton, LayoutParams(touchSize, touchSize))
            addView(bar, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }

        private fun submitCurrentInput() {
            val raw = commandInput.text?.toString().orEmpty()
            autoScrollEnabled = true
            val sent = boundSession?.writeCommand(raw) ?: false
            if (sent) {
                commandInput.setText("")
                TerminalUiHelpers.performHaptic(sendButton, confirm = true)
            } else {
                TerminalUiHelpers.performHaptic(sendButton, confirm = false)
            }
        }

        private fun renderBuffer(state: TerminalBufferState) {
            val defaultFg = TerminalUiHelpers.resolveThemeColor(this, com.google.android.material.R.attr.colorOnSurface)
            val defaultBg =
                TerminalUiHelpers.resolveThemeColor(this, com.google.android.material.R.attr.colorSurfaceContainerLow)
            val spannable = TerminalColorPalette.renderBufferToSpannable(state, defaultFg, defaultBg)
            outputTextView.text = if (spannable.isNotEmpty()) spannable else ""
            if (state.overflowDetected) {
                overflowBanner.visibility = View.VISIBLE
                overflowBanner.text = context.getString(R.string.terminal_overflow_indicator, state.droppedBytes)
            } else {
                overflowBanner.visibility = View.GONE
            }
            if (autoScrollEnabled) {
                outputScrollView.post { outputScrollView.fullScroll(View.FOCUS_DOWN) }
            }
        }

        private fun updateGridDimensions(
            widthPx: Int,
            heightPx: Int,
        ) {
            val usableWidth = (widthPx - outputTextView.paddingLeft - outputTextView.paddingRight).coerceAtLeast(0)
            val usableHeight = (heightPx - outputTextView.paddingTop - outputTextView.paddingBottom).coerceAtLeast(0)
            if (usableWidth <= 0 || usableHeight <= 0) {
                return
            }
            val charWidth = outputTextView.paint.measureText("M").coerceAtLeast(1f)
            val lineHeight = outputTextView.lineHeight.coerceAtLeast(1)
            val cols = (usableWidth / charWidth).toInt().coerceAtLeast(TerminalDimensions.MIN_COLS)
            val rows = (usableHeight / lineHeight).toInt().coerceAtLeast(TerminalDimensions.MIN_ROWS)
            boundSession?.resize(cols, rows)
        }

        companion object {
            private const val TERMINAL_TEXT_SP = 12f
            private const val INPUT_TEXT_SP = 13f
            private const val SMALL_TEXT_SP = 11f
            private const val OUTPUT_PADDING_DP = 12
            private const val INPUT_BAR_TOP_MARGIN_DP = 8
            private const val CONTROL_SPACING_DP = 8
            private const val MIN_TOUCH_TARGET_DP = 48
            private const val BANNER_PAD_H_DP = 10
            private const val BANNER_PAD_V_DP = 4
            private const val AUTOSCROLL_THRESHOLD_DP = 24
            private const val CTRL_C_BYTE: Byte = 0x03
        }
    }
