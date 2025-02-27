package ke.co.banit.idle_detector_compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
 * IdleDetectorProvider is a high-level composable that wraps a UI hierarchy to monitor
 * user inactivity and trigger a callback when the user is idle for a specified timeout period.
 *
 * This composable integrates several key dependencies:
 *
 * 1. **Jetpack Compose Foundation**:
 *    - [@Composable]: Allows this function to be used within the Compose UI tree.
 *    - [remember]: Caches the [IdleDetector] instance across recompositions.
 *    - [Box], [Modifier.fillMaxSize()], [pointerInput]: Used to create a container that
 *      fills the available space and intercepts pointer (touch) events.
 *    - [CompositionLocalProvider] & [staticCompositionLocalOf]: Provide a way to supply the
 *      idle state ([StateFlow<Boolean>]) to descendant composables.
 *
 * 2. **Lifecycle Components**:
 *    - [LocalLifecycleOwner]: Provides the current [LifecycleOwner] (typically an Activity or Fragment).
 *    - [DisposableEffect]: Manages side effects that must be cleaned up when the composition leaves scope.
 *    - [LifecycleEventObserver]: Observes lifecycle events to start and stop the idle detector appropriately.
 *
 * 3. **Kotlin Coroutines & Flow**:
 *    - [LaunchedEffect]: Launches a coroutine in the composition that collects the idle state.
 *    - [MutableStateFlow]/[StateFlow]: IdleDetector exposes its idle status as a reactive stream.
 *    - [delay]: Used internally in the IdleDetector to poll for user inactivity at a configurable interval.
 *
 * 4. **Kotlin Duration API**:
 *    - [Duration]: Used for specifying [idleTimeout] (the period after which the user is considered idle)
 *      and [checkInterval] (how often to poll for inactivity). The default check interval is set to 1 second.
 *
 * @param idleTimeout The duration after which, if no user interaction is registered, the user is considered idle.
 * @param checkInterval The polling interval to check for inactivity. Defaults to 1 second.
 * @param onIdle A callback that is invoked when the user is detected as idle.
 * @param content The UI content to be displayed. The idle detector is applied to all child composables.
 *
 * **Usage Note:**
 * The IdleDetectorProvider is best used as a wrapper around your screen content. It monitors user interactions
 * via a pointerInput modifier and leverages lifecycle events to pause/resume monitoring appropriately.
 *
 * **Important Dependencies and Considerations:**
 * - The [IdleDetector] is a utility class that monitors user interactions and determines if the user
 *   has been inactive for a specified timeout period..
 * - Lifecycle events are managed using [DisposableEffect] to ensure that the detector is started when the screen
 *   resumes and stopped when paused.
 * - User interactions are captured using [pointerInput] on a [Box] container. This approach avoids interfering
 *   with existing touch listeners since it does not consume the pointer events.
 * - The idle state is provided to the composable hierarchy via a [CompositionLocalProvider] so that any child composable
 *   can optionally read the current idle status from [LocalIdleDetectorState].
 */
@Composable
fun IdleDetectorProvider(
    idleTimeout: Duration,
    checkInterval: Duration = 1.seconds,
    onIdle: () -> Unit,
    content: @Composable () -> Unit,
) {
    // Create (and cache) an instance of IdleDetector.
    val idleDetector = remember { IdleDetector(idleTimeout, checkInterval) }

    // Obtain the current LifecycleOwner (typically the Activity or Fragment hosting the Compose UI).
    val lifecycle = LocalLifecycleOwner.current

    // Handle lifecycle events to start/stop idle detection.
    DisposableEffect(lifecycle) {
        // Create an observer that starts the detector on resume and stops it on pause.
        val observer = LifecycleEventObserver { _, lifecycleEvent ->
            when (lifecycleEvent) {
                Lifecycle.Event.ON_RESUME -> {
                    idleDetector.start()
                    // Optionally reset on resume:
                    idleDetector.registerInteraction()
                }

                Lifecycle.Event.ON_PAUSE -> idleDetector.stop()
                else -> {}
            }
        }
        // Add the observer to the lifecycle.
        lifecycle.lifecycle.addObserver(observer)
        onDispose {
            // On disposal, remove the observer and ensure idle detection is stopped.
            lifecycle.lifecycle.removeObserver(observer)
            idleDetector.stop()
        }
    }

    // Launch a coroutine that collects the idle state.
    // When the detector signals that the user is idle, the onIdle callback is invoked.
    LaunchedEffect(idleDetector) {
        idleDetector.isIdle.collect { isUserIdle ->
            if (isUserIdle) onIdle()
        }
    }

    // Provide the idle state to the composition via a CompositionLocal.
    // This allows child composables to access the idle state if needed.
    CompositionLocalProvider(
        LocalIdleDetectorState provides idleDetector.isIdle,
        LocalIdleReset provides { idleDetector.registerInteraction() }
    ) {
        // Wrap the content in a Box with a pointerInput modifier to intercept pointer events.
        // The pointerInput block runs continuously, awaiting any pointer event and registering an interaction.
        Box(
            modifier = Modifier
                .fillMaxSize()
                // Listen for key events (hardware keys, D-pad, etc.)
                .onPreviewKeyEvent {
                    idleDetector.registerInteraction()
                    false // Allow the event to propagate to focused child components
                }
                // Listen for pointer events (taps, swipes, etc.)
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent(PointerEventPass.Final)
                            idleDetector.registerInteraction()
                        }
                    }
                }
        ) {
            content()
        }

    }
}

/**
 * CompositionLocal to expose the idle state (a [StateFlow] of [Boolean]) to descendant composables.
 *
 * The default value is a [MutableStateFlow] initialized to false.
 */
val LocalIdleDetectorState = staticCompositionLocalOf<StateFlow<Boolean>> {
    MutableStateFlow(false)
}

/**
 * CompositionLocal to expose a lambda that resets the idle timer.
 * This allows any composable (including dialogs and popups) to manually register an interaction.
 */
val LocalIdleReset = staticCompositionLocalOf<(() -> Unit)?> { null }

