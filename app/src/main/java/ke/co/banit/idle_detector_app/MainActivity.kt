package ke.co.banit.idle_detector_app

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import ke.co.banit.idle_detector_app.ui.theme.IdleDetectorAppTheme
import ke.co.banit.idle_detector_compose.IdleDetectorProvider
import ke.co.banit.idle_detector_compose.IdleOrigin
import kotlin.time.Duration.Companion.seconds

class MainActivity : ComponentActivity() {
    private val TAG = "MainActivityIdleLog"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // Example 1: Using the new type-safe IdleOrigin API
            IdleDetectorProvider(
                idleTimeout = 30.seconds,
                onIdle = { origin ->
                    when (origin) {
                        IdleOrigin.Foreground -> {
                            Log.d(TAG, "User idle in foreground - showing warning")
                        }
                        IdleOrigin.Background -> {
                            Log.d(TAG, "User idle in background - could logout here")
                        }
                    }
                }
            ) {
                IdleDetectorAppTheme {
                    AppContent()
                }
            }

            // Example 2: Using Configuration Builder (commented out)
            /*
            val config = IdleDetectorConfig.Builder()
                .timeout(30.seconds)
                .checkInterval(2.seconds)
                .enableLogging()
                .build()

            IdleDetectorProvider(
                config = config,
                onIdle = { origin ->
                    when (origin) {
                        IdleOrigin.Foreground -> Log.d(TAG, "Foreground idle")
                        IdleOrigin.Background -> Log.d(TAG, "Background idle")
                    }
                }
            ) {
                IdleDetectorAppTheme {
                    AppContent()
                }
            }
            */
        }
    }
}