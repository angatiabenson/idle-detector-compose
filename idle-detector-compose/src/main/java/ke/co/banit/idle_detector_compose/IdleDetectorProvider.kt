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
 * val isIdle by LocalIdleDetectorState.current
 * if (isIdle) {
 *     ShowIdleOverlay()
 * }
 * ```
 *
 * @see LocalIdleReset
 * @see LocalIdleTimerState
 */
val LocalIdleDetectorState = compositionLocalOf<State<Boolean>> { mutableStateOf(false) }

/**
 * Composition local providing access to the timer state for countdown functionality.
 *
 * Provides real-time countdown information including remaining time, progress,
 * and warning period status.
 *
 * ## Usage
 * ```kotlin
 * val timerState by LocalIdleTimerState.current
 *
 * // Display remaining time
 * Text("Session expires in: ${timerState.formattedRemainingTime}")
 *
 * // Show progress bar
 * LinearProgressIndicator(progress = timerState.progress)
 *
 * // Check warning state
 * if (timerState.isInWarningPeriod) {
 *     ShowWarningBanner()
 * }
 * ```
 *
 * @see IdleTimerState
 * @see LocalIdleDetectorState
 */
val LocalIdleTimerState = compositionLocalOf<State<IdleTimerState>> {
    mutableStateOf(IdleTimerState.initial(Duration.INFINITE))
}

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
 * @see LocalIdleTimerState
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
 * @param warningTimeout Optional duration before idle timeout to trigger warning callback.
 * @param checkInterval The interval at which to check for user interactions (default is 1 second).
 * @param onWarning Optional callback invoked when warning period begins, with remaining time until idle.
 * @param onIdleWithOrigin Callback invoked when the application goes idle, with a boolean indicating if it was from background.
 * @param content The content to be displayed within the idle detector context.
 */
@Composable
fun IdleDetectorProvider(
    idleTimeout: Duration,
    warningTimeout: Duration? = null,
    checkInterval: Duration = 1.seconds,
    onWarning: ((Duration) -> Unit)? = null,
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
            warningTimeout = warningTimeout,
            checkInterval = checkInterval,
            onIdleWithOrigin = onIdleWithOrigin,
            onWarning = onWarning,
        )
    }

    DisposableEffect(Unit) {
        onDispose { idleDetector.cleanUp() }
    }

    CompositionLocalProvider(
        LocalIdleDetectorState provides idleDetector.isIdle,
        LocalIdleTimerState provides idleDetector.timerState,
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
 * ## With Warning Timeout
 * ```kotlin
 * val config = IdleDetectorConfig.Builder()
 *     .timeout(5.minutes)
 *     .warningTimeout(1.minutes)  // Show warning 1 minute before idle
 *     .checkInterval(1.second)
 *     .enableLogging()
 *     .build()
 *
 * IdleDetectorProvider(
 *     config = config,
 *     onWarning = { remainingTime ->
 *         showToast("Session expires in ${remainingTime.inWholeSeconds}s")
 *     },
 *     onIdle = { origin -> logout() }
 * ) {
 *     YourApp()
 * }
 * ```
 *
 * @param config The idle detector configuration
 * @param onWarning Optional callback invoked when warning period begins, with remaining time until idle
 * @param onIdle Callback with type-safe [IdleOrigin] parameter
 * @param content The app content to wrap
 *
 * @see IdleDetectorConfig
 * @see IdleOrigin
 * @see LocalIdleTimerState
 */
@Composable
@JvmName("IdleDetectorProviderWithConfig")
fun IdleDetectorProvider(
    config: IdleDetectorConfig,
    onWarning: ((Duration) -> Unit)? = null,
    onIdle: (IdleOrigin) -> Unit,
    content: @Composable () -> Unit,
) {
    // Apply logging configuration
    IdleDetectorLogger.isEnabled = config.enableLogging

    // Delegate to core implementation
    IdleDetectorProvider(
        idleTimeout = config.timeout,
        warningTimeout = config.warningTimeout,
        checkInterval = config.checkInterval,
        onWarning = onWarning,
        onIdleWithOrigin = { fromBackground: Boolean ->
            val origin = IdleOrigin.fromBoolean(fromBackground)
            onIdle(origin)
        },
        content = content
    )
}

/**
 * A Composable function that provides idle detection with warning support using type-safe callbacks.
 *
 * This overload provides the most comprehensive API with both warning and idle callbacks
 * using type-safe [IdleOrigin] for the idle callback.
 *
 * ## Usage
 * ```kotlin
 * IdleDetectorProvider(
 *     idleTimeout = 5.minutes,
 *     warningTimeout = 1.minutes,
 *     onWarning = { remainingTime ->
 *         showWarningDialog(remainingTime)
 *     },
 *     onIdle = { origin ->
 *         when (origin) {
 *             IdleOrigin.Foreground -> showIdleScreen()
 *             IdleOrigin.Background -> logoutUser()
 *         }
 *     }
 * ) {
 *     YourApp()
 * }
 * ```
 *
 * @param idleTimeout Duration of inactivity before considered idle
 * @param warningTimeout Duration before idle timeout to trigger warning (must be less than idleTimeout)
 * @param checkInterval How often to check for idle state (default: 1 second)
 * @param onWarning Callback invoked when warning period begins, with remaining time until idle
 * @param onIdle Callback with type-safe [IdleOrigin] parameter
 * @param content The app content to wrap
 *
 * @see IdleOrigin
 * @see LocalIdleTimerState
 */
@Composable
@JvmName("IdleDetectorProviderWithWarningAndIdleOrigin")
fun IdleDetectorProvider(
    idleTimeout: Duration,
    warningTimeout: Duration,
    checkInterval: Duration = 1.seconds,
    onWarning: (Duration) -> Unit,
    onIdle: (IdleOrigin) -> Unit,
    content: @Composable () -> Unit,
) = IdleDetectorProvider(
    idleTimeout = idleTimeout,
    warningTimeout = warningTimeout,
    checkInterval = checkInterval,
    onWarning = onWarning,
    onIdleWithOrigin = { fromBackground: Boolean ->
        val origin = IdleOrigin.fromBoolean(fromBackground)
        onIdle(origin)
    },
    content = content
)