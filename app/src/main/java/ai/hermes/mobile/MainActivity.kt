package ai.hermes.mobile

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat

/**
 * Primary entry point for Hermes Agent on Android.
 * Enforces edge-to-edge window insets per DESIGN-GUIDELINES.md.
 */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        title = getString(R.string.app_name)
    }
}
