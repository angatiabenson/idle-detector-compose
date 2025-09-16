package ke.co.banit.idle_detector_app

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import ke.co.banit.idle_detector_app.ui.theme.IdleDetectorAppTheme
import ke.co.banit.idle_detector_compose.IdleDetectorProvider
import kotlin.time.Duration.Companion.seconds

class MainActivity : ComponentActivity() {
    private val TAG = "MainActivityIdleLog" // Tag for logging
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            IdleDetectorProvider(
                idleTimeout = 5.seconds,
                onIdleWithOrigin = { fromBackground ->
                    Log.d(
                        TAG,
                        "User has been idle for 5 seconds. Triggering onIdle action from background - $fromBackground."
                    )
                },
                content = {
                    IdleDetectorAppTheme {
                        AppContent()
                    }
                })
        }
    }
}