package ke.co.banit.idle_detector_compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
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

// LocalIdleDetectorState: Provides a state object representing the current idle state (true if idle, false otherwise)
// LocalIdleReset: Provides a function to reset the idle timer by registering an interaction
val LocalIdleDetectorState = compositionLocalOf<State<Boolean>> { mutableStateOf(false) }
val LocalIdleReset = compositionLocalOf<(() -> Unit)?> { null }


/**
 * Backwards-compatible overload for existing users.
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
) {
    IdleDetectorProvider(
        idleTimeout = idleTimeout,
        checkInterval = checkInterval,
        onIdleWithOrigin = { _: Boolean -> onIdle() },
        content = content
    )
}

/**
 * IdleDetectorProvider is a Composable that monitors user interactions to detect idle state.
 *
 * It implements both foreground polling and background worker scheduling to detect when the user has been idle
 * for a specified duration. It exposes the idle state and a reset function (to register interactions) via CompositionLocals.
 *
 * @param idleTimeout The duration after which the app should be considered idle if no interaction occurs.
 * @param checkInterval The interval used by the foreground polling mechanism to check for idleness (default is 1 second).
 * @param onIdleWithOrigin Callback with a boolean: true if triggered from background resume, false otherwise
 * @param content The composable content that is wrapped by this provider.
 */
@Composable
fun IdleDetectorProvider(
    idleTimeout: Duration,
    checkInterval: Duration = 1.seconds,
    onIdleWithOrigin: (fromBackground: Boolean) -> Unit,
    content: @Composable () -> Unit,
) {
    // Retrieve the application context and lifecycle owner for lifecycle-aware operations.
    val context = LocalContext.current.applicationContext
    val lifecycleOwner = LocalLifecycleOwner.current
    // Get an instance of WorkManager to schedule background work.
    val workManager = remember { WorkManager.getInstance(context) }

    // State: Keeps the last known interaction timestamp in milliseconds.
    var lastInteractionTimestamp by remember { mutableLongStateOf(0L) }

    // State: Tracks whether the app is currently idle.
    // This state is provided to downstream composables via LocalIdleDetectorState.
    val isIdleState = remember { mutableStateOf(false) }

    // State: Prevents duplicate calls to the onIdle callback.
    var onIdleCalled by remember { mutableStateOf(false) }

    // This effect initializes the last interaction timestamp when the composable is first composed.
    LaunchedEffect(Unit) { // Runs once on initial composition
        var initialTimestamp = IdlePersistence.getLastInteractionTimestamp(context)
        if (initialTimestamp == 0L) {
            // If no interaction was recorded, initialize with the current system time.
            initialTimestamp = System.currentTimeMillis()
            //Log.d(TAG, "No previous interaction time found. Initializing with current time: $initialTimestamp")
            // Record the initial timestamp persistently.
            IdlePersistence.recordInteraction(context, initialTimestamp)
        } else {
            //Log.d(TAG, "Initialized with previous interaction time: $initialTimestamp")
        }
        // Update the state with the initialized timestamp.
        lastInteractionTimestamp = initialTimestamp
    }

    // --- Idle Check Logic (Runs whenever lastInteractionTimestamp or idleTimeout changes) ---
    // This effect continuously re-evaluates the idle state based on the last interaction timestamp.
    LaunchedEffect(lastInteractionTimestamp, idleTimeout) {
        // Do nothing if the timestamp is not initialized.
        if (lastInteractionTimestamp == 0L) return@LaunchedEffect

        // Calculate elapsed time since the last recorded interaction.
        val now = System.currentTimeMillis()
        val elapsed = now - lastInteractionTimestamp
        // Determine if the app should be considered idle.
        val isCurrentlyIdle = elapsed >= idleTimeout.inWholeMilliseconds

        //Log.d(TAG, "Idle Check: Now=$now, LastInteraction=$lastInteractionTimestamp, Elapsed=$elapsed, Timeout=${idleTimeout.inWholeMilliseconds}, IsCurrentlyIdle=$isCurrentlyIdle")

        // Update the idle state.
        isIdleState.value = isCurrentlyIdle

        // If the idle condition is met and the onIdle callback has not been invoked yet, trigger it.
        if (isCurrentlyIdle && !onIdleCalled) {
            //Log.d(TAG, "Idle Check: Timeout reached. Calling onIdle.")
            onIdleWithOrigin(false)
            onIdleCalled = true
        } else if (!isCurrentlyIdle && onIdleCalled) {
            // Reset the flag if the user becomes active again.
            //Log.d(TAG, "Idle Check: Became active, resetting onIdleCalled flag.")
            onIdleCalled = false
        }
    }

    // --- Function to Register Interaction ---
    // This function updates the last interaction timestamp, resets the idle state, and cancels any pending background work.
    val registerInteraction: () -> Unit = remember {
        {
            val now = System.currentTimeMillis()
            //Log.d(TAG, "Interaction Registered at: $now")
            // Update the state with the current interaction time.
            lastInteractionTimestamp = now
            // Persist the new interaction timestamp.
            IdlePersistence.recordInteraction(context, now)
            // Immediately set the idle state to active.
            isIdleState.value = false
            // Reset the onIdle callback flag if it was previously set.
            if (onIdleCalled) {
                //Log.d(TAG, "Interaction reset onIdleCalled flag.")
                onIdleCalled = false
            }
            // Cancel any scheduled background worker since a new interaction has occurred.
            cancelBackgroundTimeoutWorker(workManager)
        }
    }

    // --- Lifecycle Management ---
    // This DisposableEffect manages the app's lifecycle events to control foreground polling and background scheduling.
    DisposableEffect(lifecycleOwner, idleTimeout) {
        // Foreground polling timer job: Runs periodic checks when the app is in the foreground.
        val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        var pollingTimerJob: Job? = null

        // Create a lifecycle observer to respond to lifecycle events.
        val observer = LifecycleEventObserver { owner, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> {
                    ////Log.d(TAG, "Lifecycle: ON_CREATE")
                    lastInteractionTimestamp = 0L
                    isIdleState.value = false
                    onIdleCalled = false
                    IdlePersistence.reset(context)
                }

                Lifecycle.Event.ON_RESUME -> {
                    ////Log.d(TAG, "Lifecycle: ON_RESUME")
                    // Cancel any background worker when the app returns to the foreground.
                    cancelBackgroundTimeoutWorker(workManager)

                    // --- Determine Initial State Based on Last Interaction ---
                    val currentTime = System.currentTimeMillis()
                    val timeSinceLast = currentTime - lastInteractionTimestamp
                    val shouldBeIdleInitially = timeSinceLast >= idleTimeout.inWholeMilliseconds

                    // --- Check for Background Timeout Flag ---
                    // If the background worker already triggered an idle state, call onIdle immediately.
                    val backgroundTimeoutTriggered =
                        IdlePersistence.isBackgroundTimeoutTriggered(context)
                    if (backgroundTimeoutTriggered) {
                        // //Log.w(TAG, "ON_RESUME: Background timeout flag was set. Triggering onIdle immediately.")
                        // Clear the background timeout flag.
                        IdlePersistence.setBackgroundTimeoutTriggered(context, false)
                        if (!onIdleCalled) {
                            onIdleWithOrigin(true)
                            onIdleCalled = true
                        }

                    } else {
                        isIdleState.value = shouldBeIdleInitially

                        if (shouldBeIdleInitially) {
                            // App was already idle or became idle during the pause
                            if (!onIdleCalled) {
                                ////Log.d(TAG, "ON_RESUME: Detected idle state on resume. Calling onIdle.")
                                onIdleWithOrigin(false)
                                onIdleCalled = true
                            }
                        } else {
                            // App is genuinely active on resume
                            onIdleCalled = false
                        }
                    }

                    // Start a foreground polling timer to force regular state re-checks.
                    pollingTimerJob?.cancel()
                    pollingTimerJob = scope.launch {
                        while (isActive) {
                            delay(checkInterval.inWholeMilliseconds)
                            // Re-evaluate the idle state without updating the last interaction timestamp.
                            val current = System.currentTimeMillis()
                            val timeSinceLast = current - lastInteractionTimestamp
                            val shouldBeIdle = timeSinceLast >= idleTimeout.inWholeMilliseconds
                            if (isIdleState.value != shouldBeIdle) {
                                //Log.d(TAG, "Polling timer forcing state update to: $shouldBeIdle")
                                isIdleState.value = shouldBeIdle
                            }
                            // Trigger the onIdle callback if idle state is detected and hasn't been handled yet.
                            if (shouldBeIdle && !onIdleCalled) {
                                //Log.d(TAG, "Polling timer detected idle. Calling onIdle.")
                                onIdleWithOrigin(false)
                                onIdleCalled = true
                            }
                        }
                    }
                }

                Lifecycle.Event.ON_PAUSE -> {
                    //Log.d(TAG, "Lifecycle: ON_PAUSE")
                    // Stop the foreground polling timer when the app moves to the background.
                    pollingTimerJob?.cancel()
                    // Schedule a background worker to handle idle state detection while the app is in the background.
                    scheduleBackgroundTimeoutWorker(
                        workManager,
                        idleTimeout,
                        lastInteractionTimestamp
                    )
                }

                Lifecycle.Event.ON_DESTROY -> {
                    //Log.d(TAG, "Lifecycle: ON_DESTROY")
                    // Cancel the polling timer and background worker on destruction.
                    scope.cancel()
                    cancelBackgroundTimeoutWorker(workManager)
                }

                else -> {}
            }
        }
        // Register the observer with the lifecycle.
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            //Log.d(TAG, "DisposableEffect: onDispose")
            // Clean up by removing the observer and cancelling the timer and worker.
            lifecycleOwner.lifecycle.removeObserver(observer)
            scope.cancel()
            cancelBackgroundTimeoutWorker(workManager)
        }
    }

    // --- Provide State and Reset Function to the Composition ---
    // CompositionLocalProvider makes the idle state and the reset function available to child composables.
    CompositionLocalProvider(
        LocalIdleDetectorState provides isIdleState, // Provides the current idle state.
        LocalIdleReset provides registerInteraction // Provides the function to register interactions.
    ) {
        // The Box acts as a container that intercepts key and pointer events to register interactions.
        Box(
            modifier = Modifier
                .fillMaxSize()
                // Capture key events to reset the idle timer.
                .onPreviewKeyEvent {
                    registerInteraction()
                    false // Allow further propagation of the event.
                }
                // Capture pointer events to reset the idle timer.
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            // Wait for a pointer event and register the interaction.
                            awaitPointerEvent(PointerEventPass.Initial)
                            registerInteraction()
                        }
                    }
                }
        ) {
            // Render the provided content within this provider.
            content()
        }
    }
}

/**
 * Schedules a background worker to monitor idle timeout while the app is in the background.
 *
 * The worker is scheduled based on the remaining time until the idle timeout is reached. If the timeout has already
 * been met, the worker is not scheduled.
 *
 * @param workManager The WorkManager instance to schedule the background worker.
 * @param timeout The idle timeout duration.
 * @param lastInteractionTime The last recorded interaction timestamp.
 */
private fun scheduleBackgroundTimeoutWorker(
    workManager: WorkManager,
    timeout: Duration,
    lastInteractionTime: Long, // Explicitly pass the last interaction timestamp.
) {
    if (lastInteractionTime == 0L) {
        //Log.w(TAG, "Skipping background worker schedule: No valid last interaction time.")
        return
    }

    val currentTime = System.currentTimeMillis()
    val elapsedMillis = currentTime - lastInteractionTime
    val timeoutMillis = timeout.inWholeMilliseconds

    // Calculate the delay before the idle timeout should trigger.
    val remainingDelay = (timeoutMillis - elapsedMillis).coerceAtLeast(0)

    //Log.d(TAG, "Scheduling Worker: Now=${currentTime}, LastInteraction=${lastInteractionTime}, Elapsed=${elapsedMillis}ms, Timeout=${timeoutMillis}ms, CalculatedDelay=${remainingDelay}ms")

    // If the timeout is already met, skip scheduling the worker.
    if (remainingDelay <= 0 && elapsedMillis >= timeoutMillis) {
        //Log.d(TAG, "Skipping worker schedule: Timeout already met or exceeded.")
        return
    }

    // Prepare the input data for the background worker.
    val data = workDataOf(BackgroundTimeoutWorker.KEY_TIMEOUT_MILLIS to timeoutMillis)

    // Build the background worker request.
    val workRequest = OneTimeWorkRequestBuilder<BackgroundTimeoutWorker>()
        // Ensure a small minimum delay to avoid immediate execution edge cases.
        .setInitialDelay(remainingDelay.coerceAtLeast(50), TimeUnit.MILLISECONDS)
        .setInputData(data)
        .build()

    // Enqueue the worker as unique work to prevent duplicate scheduling.
    workManager.enqueueUniqueWork(
        BackgroundTimeoutWorker.WORK_NAME,
        ExistingWorkPolicy.REPLACE,
        workRequest
    )
}

/**
 * Cancels any scheduled background timeout worker.
 *
 * This function cancels the unique work associated with the background idle detection worker.
 *
 * @param workManager The WorkManager instance used to cancel the worker.
 */
private fun cancelBackgroundTimeoutWorker(workManager: WorkManager) {
    workManager.cancelUniqueWork(BackgroundTimeoutWorker.WORK_NAME)
}


