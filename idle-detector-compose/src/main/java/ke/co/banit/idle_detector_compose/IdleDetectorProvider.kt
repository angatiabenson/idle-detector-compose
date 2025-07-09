package ke.co.banit.idle_detector_compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/*
 * ------------------------------------------------------------------------
 * Project: idle-detector-app
 * File Created by: Angatia Benson on Thu, Feb 13, 2025
 * ------------------------------------------------------------------------
 * © 2025 Angatia Benson. All rights reserved.
 * ------------------------------------------------------------------------
 */
// Composition Local Providers
val LocalIdleDetectorState = compositionLocalOf<State<Boolean>> { mutableStateOf(false) }
val LocalIdleReset = compositionLocalOf<(() -> Unit)?> { null }

/**
 * A Composable function that provides an idle detection mechanism for the application.
 *
 * This function sets up an idle detector that monitors user interactions and determines if the application
 * has been idle for a specified duration. It provides a callback to notify when the application goes idle,
 * along with an origin flag indicating whether the idle state was triggered from the background.
 *
 * @param idleTimeout The duration after which the application is considered idle.
 * @param checkInterval The interval at which to check for user interactions (default is 1 second).
 * @param onIdleWithOrigin Callback invoked when the application goes idle, with a boolean indicating if it was from background.
 * @param content The content to be displayed within the idle detector context.
 */
@Composable
fun IdleDetectorProvider(
    idleTimeout: Duration,
    checkInterval: Duration = 1.seconds,
    onIdleWithOrigin: (Boolean) -> Unit,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val lifecycleOwner = LocalLifecycleOwner.current

    val idleDetector = remember {
        IdleDetector(
            context = context,
            lifecycleOwner = lifecycleOwner,
            idleTimeout = idleTimeout,
            checkInterval = checkInterval,
            onIdleWithOrigin = onIdleWithOrigin
        )
    }

    DisposableEffect(Unit) {
        onDispose { idleDetector.cleanUp() }
    }

    CompositionLocalProvider(
        LocalIdleDetectorState provides idleDetector.isIdle,
        LocalIdleReset provides idleDetector::registerInteraction
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onPreviewKeyEvent {
                    idleDetector.registerInteraction()
                    false
                }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent(PointerEventPass.Initial)
                            idleDetector.registerInteraction()
                        }
                    }
                }
        ) {
            content()
        }
    }
}

// Backwards-compatible overload

/** @deprecated Use the new `onIdleWithOrigin` callback instead.
 * This overload is provided for compatibility with previous versions.
 *
 * @param idleTimeout The duration after which the application is considered idle.
 * @param checkInterval The interval at which to check for user interactions (default is 1 second).
 * @param onIdle Callback invoked when the application goes idle.
 * @param content The content to be displayed within the idle detector context.
 */
@Deprecated(
    message = "Use onIdle callback with the `fromBackground` flag",
    replaceWith = ReplaceWith(
        "IdleDetectorProvider(idleTimeout, checkInterval, { fromBackground -> onIdle() }, content)"
    )
)
@Composable
fun IdleDetectorProvider(
    idleTimeout: Duration,
    checkInterval: Duration = 1.seconds,
    onIdle: () -> Unit,
    content: @Composable () -> Unit,
) = IdleDetectorProvider(
    idleTimeout = idleTimeout,
    checkInterval = checkInterval,
    onIdleWithOrigin = { _ -> onIdle() },
    content = content
)