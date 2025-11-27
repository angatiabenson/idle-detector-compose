package ke.co.banit.idle_detector_compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * Copyright 2025 Angatia Benson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * Pre-built UI components for common idle detection scenarios.
 *
 * These components provide ready-to-use dialogs, indicators, and warnings
 * that integrate seamlessly with [IdleDetectorProvider].
 */

/**
 * Displays a warning dialog when the user becomes idle.
 *
 * This dialog automatically appears when the idle state is detected and provides
 * options to extend the session or logout. The idle timer is automatically reset
 * when the user chooses to extend the session.
 *
 * ## Usage
 * ```kotlin
 * IdleDetectorProvider(...) {
 *     YourApp()
 *     IdleWarningDialog(
 *         onExtendSession = {
 *             analytics.track("session_extended")
 *         },
 *         onLogout = {
 *             navController.navigate("login")
 *         }
 *     )
 * }
 * ```
 *
 * @param onExtendSession Callback invoked when user chooses to extend session
 * @param onLogout Callback invoked when user chooses to logout
 * @param modifier Modifier for the dialog
 * @param title Dialog title text
 * @param message Dialog message text
 * @param confirmText Text for the extend session button
 * @param dismissText Text for the logout button
 */
@Composable
fun IdleWarningDialog(
    onExtendSession: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Session Expiring",
    message: String = "You've been inactive. Your session will expire soon.",
    confirmText: String = "Stay Logged In",
    dismissText: String = "Logout",
) {
    val isIdle by LocalIdleDetectorState.current
    val resetIdle = LocalIdleReset.current

    if (isIdle) {
        AlertDialog(
            onDismissRequest = { /* Prevent dismissal by clicking outside */ },
            title = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall
                )
            },
            text = {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        resetIdle()
                        onExtendSession()
                    }
                ) {
                    Text(confirmText)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = onLogout
                ) {
                    Text(dismissText)
                }
            },
            modifier = modifier
        )
    }
}

/**
 * Displays a visual indicator showing the current idle state.
 *
 * Shows a colored circle that changes based on the idle state:
 * - Active state: Green circle
 * - Idle state: Orange/Red circle
 *
 * Useful for status bars or debug overlays.
 *
 * ## Usage
 * ```kotlin
 * // In your status bar or toolbar
 * Row {
 *     IdleStatusIndicator()
 *     Text("Status")
 * }
 * ```
 *
 * @param modifier Modifier for the indicator
 * @param activeColor Color shown when active (default: Green)
 * @param idleColor Color shown when idle (default: Orange)
 * @param size Size of the indicator circle (default: 12.dp)
 */
@Composable
fun IdleStatusIndicator(
    modifier: Modifier = Modifier,
    activeColor: Color = Color.Green,
    idleColor: Color = Color(0xFFFF9800), // Orange
    size: Dp = 12.dp,
) {
    val isIdle by LocalIdleDetectorState.current

    Surface(
        modifier = modifier.size(size),
        shape = CircleShape,
        color = if (isIdle) idleColor else activeColor
    ) {}
}

/**
 * Displays a simple info box showing the current idle state.
 *
 * Shows text-based status with customizable labels. Useful for debugging
 * or providing visual feedback about the idle detection state.
 *
 * ## Usage
 * ```kotlin
 * // In your debug screen or settings
 * Column {
 *     IdleStateLabel()
 *     // Other debug info
 * }
 * ```
 *
 * @param modifier Modifier for the label
 * @param activeLabel Text shown when active (default: "Active")
 * @param idleLabel Text shown when idle (default: "Idle")
 * @param showIndicator Whether to show a color indicator alongside text
 */
@Composable
fun IdleStateLabel(
    modifier: Modifier = Modifier,
    activeLabel: String = "Active",
    idleLabel: String = "Idle",
    showIndicator: Boolean = true,
) {
    val isIdle by LocalIdleDetectorState.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showIndicator) {
            IdleStatusIndicator()
            Spacer(modifier = Modifier.width(8.dp))
        }

        Text(
            text = if (isIdle) idleLabel else activeLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isIdle) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.primary
            }
        )
    }
}

/**
 * A simple banner that appears when the user is idle.
 *
 * Shows a warning message with an option to dismiss and reset the idle state.
 * Less intrusive than a full dialog.
 *
 * ## Usage
 * ```kotlin
 * // At the top of your screen
 * Column {
 *     IdleBanner(
 *         onDismiss = {
 *             analytics.track("idle_banner_dismissed")
 *         }
 *     )
 *     YourContent()
 * }
 * ```
 *
 * @param onDismiss Callback invoked when user dismisses the banner
 * @param modifier Modifier for the banner
 * @param message Message to display in the banner
 * @param actionText Text for the dismiss button
 */
@Composable
fun IdleBanner(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    message: String = "You've been inactive for a while",
    actionText: String = "I'm here",
) {
    val isIdle by LocalIdleDetectorState.current
    val resetIdle = LocalIdleReset.current

    if (isIdle) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.errorContainer,
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.weight(1f)
                )

                Button(
                    onClick = {
                        resetIdle()
                        onDismiss()
                    }
                ) {
                    Text(actionText)
                }
            }
        }
    }
}

/**
 * A compact idle warning that can be embedded in existing UI.
 *
 * Shows a warning message with a reset button inline. Less prominent than
 * a dialog or banner, suitable for scenarios where you want to notify the
 * user without blocking their workflow.
 *
 * ## Usage
 * ```kotlin
 * // Inside your existing layout
 * Column {
 *     YourContent()
 *     IdleWarningInline()
 * }
 * ```
 *
 * @param modifier Modifier for the warning
 * @param message Warning message to display
 * @param actionText Text for the action button
 * @param onAction Optional callback when action is performed
 */
@Composable
fun IdleWarningInline(
    modifier: Modifier = Modifier,
    message: String = "Inactive - session may expire",
    actionText: String = "Keep Active",
    onAction: (() -> Unit)? = null,
) {
    val isIdle by LocalIdleDetectorState.current
    val resetIdle = LocalIdleReset.current

    if (isIdle) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.size(8.dp))

            OutlinedButton(
                onClick = {
                    resetIdle()
                    onAction?.invoke()
                }
            ) {
                Text(actionText)
            }
        }
    }
}
