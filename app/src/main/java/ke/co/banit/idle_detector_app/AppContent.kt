package ke.co.banit.idle_detector_app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
    // Track the number of times the button is tapped.
    var interactionCount by remember { mutableIntStateOf(0) }
    // State to control the visibility of the dialog.
    var showDialog by remember { mutableStateOf(false) }
    // Observe the idle state (true if idle, false if active).
    val isSessionIdle by LocalIdleDetectorState.current
    val idleReset = LocalIdleReset.current


    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = if (isSessionIdle) "IDLE STATE ⚠️" else "ACTIVE STATE ✅",
            style = MaterialTheme.typography.displaySmall,
            color = if (isSessionIdle)
                MaterialTheme.colorScheme.error
            else
                MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Button that increases interaction count
        Button(
            onClick = { interactionCount++ },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isSessionIdle)
                    MaterialTheme.colorScheme.error
                else
                    MaterialTheme.colorScheme.primary
            )
        ) {
            Text("Tap me (${interactionCount}x)")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // New button that opens a dialog for testing idle detection
        Button(
            onClick = { showDialog = true },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary
            )
        ) {
            Text("Open Dialog")
        }
        Spacer(modifier = Modifier.height(16.dp))

        TextField(
            value = "",
            onValueChange = {
                idleReset?.invoke()
            },
        )
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = if (isSessionIdle)
                "Touch screen to resume!"
            else
                "Don't interact for 5 seconds to test",
            style = MaterialTheme.typography.bodyMedium
        )
    }

    // The dialog appears when showDialog is true.
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Test Dialog") },
            text = { Text("Click confirm to reset idle timer.") },
            confirmButton = {
                Button(
                    onClick = {
                        idleReset?.invoke()
                        showDialog = false
                    }
                ) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showDialog = false }
                ) {
                    Text("Dismiss")
                }
            }
        )
    }
}