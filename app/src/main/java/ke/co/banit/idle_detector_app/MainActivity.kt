package ke.co.banit.idle_detector_app

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ke.co.banit.idle_detector_app.ui.theme.IdledetectorappTheme
import ke.co.banit.idle_detector_compose.IdleDetectorProvider
import ke.co.banit.idle_detector_compose.LocalIdleDetectorState
import kotlin.time.Duration.Companion.seconds

class MainActivity : ComponentActivity() {
    private val TAG = "MainActivityIdleLog" // Tag for logging
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            IdleDetectorProvider(
                idleTimeout = 5.seconds, // Set the idle timeout: 5 seconds of inactivity triggers onIdle.
                onIdle = {
                    Log.d(TAG, "User has been idle for 5 seconds. Triggering onIdle action.")
                    // Callback triggered when the user becomes idle.
                    // For instance, you could display an idle warning dialog or navigate to a lock screen.
                }
            ) {
                IdledetectorappTheme {
                    AppContent()
                }
            }
        }
    }
}