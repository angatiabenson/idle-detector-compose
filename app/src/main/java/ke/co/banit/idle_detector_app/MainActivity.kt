package ke.co.banit.idle_detector_app

import android.os.Bundle
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            IdleDetectorProvider(
                idleTimeout = 5.seconds, // Set the idle timeout: 5 seconds of inactivity triggers onIdle.
                onIdle = {
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
@Composable
private fun AppContent() {
    // Local state tracking the number of times the button is tapped.
    var interactionCount by remember { mutableIntStateOf(0) }
    // Observe the idle state (true if the session is idle, false otherwise) from IdleDetectorProvider.
    val isSessionIdle by LocalIdleDetectorState.current.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Display a text status indicating whether the user session is idle.
        Text(
            text = if (isSessionIdle) "IDLE STATE ⚠️" else "ACTIVE STATE ✅",
            style = MaterialTheme.typography.displaySmall,
            color = if (isSessionIdle)
                MaterialTheme.colorScheme.error
            else
                MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Provide an interactive button that increments the interaction count when tapped.
        Button(
            onClick = { interactionCount++ },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isSessionIdle)
                    MaterialTheme.colorScheme.error
                else
                    MaterialTheme.colorScheme.primary
            )
        ) {
            // Display the current tap count on the button.
            Text("Tap me (${interactionCount}x)")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Show contextual help text guiding the user.
        Text(
            text = if (isSessionIdle)
                "Touch screen to resume!"
            else
                "Don't interact for 5 seconds to test",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}