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
/**
 * Composition local providing access to the idle state as a [State]<[Boolean]>.
 *
 * Consumers can collect this state to observe idle status changes in real-time.
 *
 * ## Usage
 * ```kotlin
 * val isIdle by LocalIdleDetectorState.current.collectAsState()
 * if (isIdle) {
 *     ShowIdleOverlay()
 * }
 * ```
 *
 * @see LocalIdleReset
 */
val LocalIdleDetectorState = compositionLocalOf<State<Boolean>> { mutableStateOf(false) }

/**
 * Composition local providing a function to manually reset the idle timer.
 *
 * Invoking this function marks the current time as the last interaction,
 * effectively resetting the idle timeout countdown.
 *
 * ## Usage
 * ```kotlin
 * val resetIdle = LocalIdleReset.current
 * Button(onClick = resetIdle) {
 *     Text("I'm still here")
 * }
 * ```
 *
 * @throws IllegalStateException if accessed outside [IdleDetectorProvider] scope
 * @see LocalIdleDetectorState
 */
val LocalIdleReset = compositionLocalOf<() -> Unit> {
    error("""
        IdleDetectorProvider not found in composition hierarchy.
        Ensure LocalIdleReset is accessed within IdleDetectorProvider's content.

        Example:
        IdleDetectorProvider(...) {
            YourApp() // Access LocalIdleReset here
        }
    """.trimIndent())
}

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
                    var lastInteractionTime = 0L
                    val debounceMs = 100L // Debounce window to reduce overhead

                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent(PointerEventPass.Initial)

                            // Debounce rapid interactions to reduce overhead
                            val now = System.currentTimeMillis()
                            if (now - lastInteractionTime >= debounceMs) {
                                idleDetector.registerInteraction()
                                lastInteractionTime = now
                            }
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
    onIdleWithOrigin = { _: Boolean -> onIdle() },
    content = content
)

/**
 * A Composable function that provides idle detection with type-safe [IdleOrigin] callback.
 *
 * This overload uses [IdleOrigin] instead of a boolean flag for better type safety
 * and code clarity when handling idle state from different origins.
 *
 * ## Usage
 * ```kotlin
 * IdleDetectorProvider(
 *     idleTimeout = 5.minutes,
 *     onIdle = { origin ->
 *         when (origin) {
 *             IdleOrigin.Foreground -> showWarningDialog()
 *             IdleOrigin.Background -> logoutUser()
 *         }
 *     }
 * ) {
 *     YourApp()
 * }
 * ```
 *
 * @param idleTimeout Duration of inactivity before considered idle
 * @param checkInterval How often to check for idle state (default: 1 second)
 * @param onIdle Callback with type-safe [IdleOrigin] parameter
 * @param content The app content to wrap
 *
 * @see IdleOrigin
 * @see IdleDetectorConfig
 */
@Composable
@JvmName("IdleDetectorProviderWithIdleOrigin")
fun IdleDetectorProvider(
    idleTimeout: Duration,
    checkInterval: Duration = 1.seconds,
    onIdle: (IdleOrigin) -> Unit,
    content: @Composable () -> Unit,
) = IdleDetectorProvider(
    idleTimeout = idleTimeout,
    checkInterval = checkInterval,
    onIdleWithOrigin = { fromBackground: Boolean ->
        val origin = IdleOrigin.fromBoolean(fromBackground)
        onIdle(origin)
    },
    content = content
)

/**
 * A Composable function that provides idle detection using a configuration object.
 *
 * This overload accepts an [IdleDetectorConfig] for a more structured and
 * type-safe configuration approach, particularly useful when you need to
 * customize multiple parameters.
 *
 * ## Basic Usage
 * ```kotlin
 * val config = IdleDetectorConfig.Builder()
 *     .timeout(5.minutes)
 *     .checkInterval(2.seconds)
 *     .enableLogging()
 *     .build()
 *
 * IdleDetectorProvider(
 *     config = config,
 *     onIdle = { origin ->
 *         when (origin) {
 *             IdleOrigin.Foreground -> showWarning()
 *             IdleOrigin.Background -> logout()
 *         }
 *     }
 * ) {
 *     YourApp()
 * }
 * ```
 *
 * ## Advanced Configuration
 * ```kotlin
 * val config = IdleDetectorConfig.Builder()
 *     .timeout(10.minutes)
 *     .checkInterval(1.second)
 *     .enableLogging()
 *     .disableBackgroundDetection()
 *     .interactionDebounce(150.milliseconds)
 *     .persistenceDebounce(1.second)
 *     .build()
 * ```
 *
 * @param config The idle detector configuration
 * @param onIdle Callback with type-safe [IdleOrigin] parameter
 * @param content The app content to wrap
 *
 * @see IdleDetectorConfig
 * @see IdleOrigin
 */
@Composable
@JvmName("IdleDetectorProviderWithConfig")
fun IdleDetectorProvider(
    config: IdleDetectorConfig,
    onIdle: (IdleOrigin) -> Unit,
    content: @Composable () -> Unit,
) {
    // Apply logging configuration
    IdleDetectorLogger.isEnabled = config.enableLogging

    // Delegate to core implementation
    IdleDetectorProvider(
        idleTimeout = config.timeout,
        checkInterval = config.checkInterval,
        onIdleWithOrigin = { fromBackground: Boolean ->
            val origin = IdleOrigin.fromBoolean(fromBackground)
            onIdle(origin)
        },
        content = content
    )
}