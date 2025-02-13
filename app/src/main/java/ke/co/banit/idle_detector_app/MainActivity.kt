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
import androidx.compose.runtime.mutableStateOf
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
                idleTimeout = 5.seconds,
                onIdle = { /* Could show dialog or navigate here */ }
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
    var interactionCount by remember { mutableStateOf(0) }
    val isSessionIdle by LocalIdleDetectorState.current.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Display current state
        Text(
            if (isSessionIdle) "IDLE STATE ⚠️" else "ACTIVE STATE ✅",
            style = MaterialTheme.typography.displaySmall,
            color = if (isSessionIdle) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.height(24.dp))

        // Interactive element
        Button(
            onClick = { interactionCount++ },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isSessionIdle) MaterialTheme.colorScheme.errorContainer
                else MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Text("Tap me (${interactionCount}x)")
        }

        Spacer(Modifier.height(16.dp))

        // Help text
        Text(
            text = if (isSessionIdle) "Touch screen to resume!"
            else "Don't interact for 5 seconds to test",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}