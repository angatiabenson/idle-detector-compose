package ke.co.banit.idle_detector_app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ke.co.banit.idle_detector_compose.IdleBanner
import ke.co.banit.idle_detector_compose.IdleStateLabel
import ke.co.banit.idle_detector_compose.IdleStatusIndicator
import ke.co.banit.idle_detector_compose.IdleWarningDialog
import ke.co.banit.idle_detector_compose.IdleWarningInline
import ke.co.banit.idle_detector_compose.LocalIdleDetectorState
import ke.co.banit.idle_detector_compose.LocalIdleReset

/**
 * ------------------------------------------------------------------------
 * Project: idle-detector-app
 * File Created by: Angatia Benson on Thu, Feb 13, 2025
 * ------------------------------------------------------------------------
 * © 2025 Angatia Benson. All rights reserved.
 * ------------------------------------------------------------------------
 */

@Composable
fun AppContent() {
    // Track the number of times the button is tapped
    var interactionCount by remember { mutableIntStateOf(0) }

    // Observe the idle state
    val isSessionIdle by LocalIdleDetectorState.current
    val idleReset = LocalIdleReset.current

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // 1. IdleBanner - Non-intrusive top banner
        IdleBanner(
            onDismiss = {
                // Track analytics or perform action on dismiss
            }
        )

        // 2. Main Content with scroll
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header with status
            Text(
                text = "Idle Detector Demo",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 3. IdleStateLabel - Shows current state with indicator
            IdleStateLabel(
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider()

            // Status Section
            Text(
                text = "Status Indicators",
                style = MaterialTheme.typography.titleMedium
            )

            // 4. IdleStatusIndicator - Colored dot
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IdleStatusIndicator(size = 16.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isSessionIdle) "Idle" else "Active",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            HorizontalDivider()

            // Interaction Section
            Text(
                text = "Test Interactions",
                style = MaterialTheme.typography.titleMedium
            )

            Button(
                onClick = { interactionCount++ },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSessionIdle)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Tap to Interact (${interactionCount}x)")
            }

            Button(
                onClick = { idleReset() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Text("Reset Timer Manually")
            }

            TextField(
                value = "",
                onValueChange = { /* Auto-resets on interaction */ },
                placeholder = { Text("Type to reset timer") },
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider()

            // Instructions
            Text(
                text = "Instructions",
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                text = "Wait 30 seconds without interacting to trigger idle state. " +
                        "You'll see various UI components react to the idle state:\n\n" +
                        "• Banner at top\n" +
                        "• Status indicators change\n" +
                        "• Inline warning appears\n" +
                        "• Dialog shows (if idle persists)",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 5. IdleWarningInline - Compact warning
            IdleWarningInline(
                onAction = {
                    // Track that user acknowledged warning
                }
            )
        }
    }

    // 6. IdleWarningDialog - Full-screen modal (shown when idle)
    IdleWarningDialog(
        onExtendSession = {
            // Analytics: user extended session
        },
        onLogout = {
            // Handle logout navigation
            // For demo, just reset
            idleReset()
        }
    )
}