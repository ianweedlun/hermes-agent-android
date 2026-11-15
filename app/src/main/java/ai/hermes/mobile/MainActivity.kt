package ai.hermes.mobile

import ai.hermes.mobile.core.HermesNotificationManager
import ai.hermes.mobile.databinding.ActivityMainBinding
import ai.hermes.mobile.engine.StorageTopology
import ai.hermes.mobile.terminal.TerminalDrawerManager
import ai.hermes.mobile.terminal.TerminalPtyBridge
import ai.hermes.mobile.terminal.TerminalSession
import ai.hermes.mobile.ui.HermesWebViewManager
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope

/**
 * Primary entry point for Hermes Agent on Android.
 * Enforces edge-to-edge window insets, wires the hardened WebView shell,
 * and hosts the native sliding terminal drawer & PTY session per DESIGN-GUIDELINES.md.
 */
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var terminalSession: TerminalSession
    private lateinit var ptyBridge: TerminalPtyBridge
    private lateinit var terminalDrawerManager: TerminalDrawerManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupInsets()
        setupWebView()
        setupTerminalDrawer()
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (::terminalDrawerManager.isInitialized) {
            terminalDrawerManager.onConfigurationChanged(newConfig)
        }
    }

    override fun onDestroy() {
        if (::terminalDrawerManager.isInitialized) {
            terminalDrawerManager.detach()
        }
        if (::ptyBridge.isInitialized) {
            ptyBridge.stop()
        }
        super.onDestroy()
    }

    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.appBarLayout) { view, insets ->
            val bars =
                insets.getInsets(
                    WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout(),
                )
            view.setPadding(bars.left, bars.top, bars.right, view.paddingBottom)
            insets
        }

        val baseSheetBottomPadding = binding.terminalBottomSheet.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.terminalBottomSheet) { view, insets ->
            val bottomInsets =
                insets.getInsets(
                    WindowInsetsCompat.Type.navigationBars() or WindowInsetsCompat.Type.ime(),
                )
            view.setPadding(
                view.paddingLeft,
                view.paddingTop,
                view.paddingRight,
                baseSheetBottomPadding + bottomInsets.bottom,
            )
            insets
        }
    }

    private fun setupWebView() {
        HermesWebViewManager.configure(binding.webView)
        binding.webView.loadUrl(DEFAULT_UI_URL)
    }

    private fun setupTerminalDrawer() {
        val topology = StorageTopology(this)
        topology.initialize()
        terminalSession = TerminalSession()
        terminalSession.appendOutput("${getString(R.string.terminal_sheet_default_log)}\n")
        ptyBridge =
            TerminalPtyBridge.createDefault(
                filesDir = topology.filesDir,
                workspaceDir = topology.workspaceDir,
                session = terminalSession,
            )
        terminalDrawerManager = TerminalDrawerManager(this, binding, terminalSession)
        terminalDrawerManager.attach(lifecycleScope)
        ptyBridge.start()
    }

    private fun handleIntent(targetIntent: Intent?) {
        if (targetIntent?.action == HermesNotificationManager.ACTION_OPEN_TERMINAL &&
            ::terminalDrawerManager.isInitialized
        ) {
            binding.coordinatorLayout.post { terminalDrawerManager.setExpanded(true) }
        }
    }

    companion object {
        const val DEFAULT_UI_URL = "http://127.0.0.1:8080/"
    }
}
