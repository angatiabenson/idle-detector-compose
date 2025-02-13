package ke.co.banit.idle_detector_compose

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration

/*
 * ------------------------------------------------------------------------
 * Project: idle-detector-app
 * File Created by: Angatia Benson on Thu, Feb 13, 2025
 * ------------------------------------------------------------------------
 * © 2025 Angatia Benson. All rights reserved.
 * ------------------------------------------------------------------------
 */

/**
 * IdleDetector is a utility class that monitors user interactions and determines if the user
 * has been inactive for a specified timeout period.
 *
 * This class is designed to be used in a Jetpack Compose environment where idle detection
 * is needed (for example, for auto-logout or to trigger specific UI events after a period
 * of inactivity). It leverages Kotlin coroutines and StateFlow to periodically poll for
 * inactivity and expose the current idle state.
 *
 * ### Dependencies:
 * - **Kotlin Coroutines**: Used for asynchronous polling via [CoroutineScope], [Job],
 *   [Dispatchers.Main], and [delay]. The [SupervisorJob] is combined with the main dispatcher
 *   to ensure that failures in this scope do not cancel sibling coroutines.
 * - **Kotlinx.coroutines.flow.StateFlow**: Used to expose the idle state (_isIdle) to observers
 *   in a reactive manner.
 * - **Kotlin Duration**: Represents both the [timeout] duration (the period after which the user
 *   is considered idle) and the [pollingInterval] (how frequently the detector checks for inactivity).
 *
 * @property timeout The duration after which the user is considered idle if no interaction is registered.
 * @property pollingInterval The interval at which the detector polls to check if the user is idle.
 */
internal class IdleDetector(
    private val timeout: Duration,
    private val pollingInterval: Duration,
) {
    /**
     * Records the timestamp (in milliseconds) of the most recent user interaction.
     * This value is updated whenever [registerInteraction] is called.
     */
    private var lastInteraction = System.currentTimeMillis()

    /**
     * A [MutableStateFlow] that holds the current idle state.
     * - `false` indicates that the user is active.
     * - `true` indicates that the user has been idle for at least [timeout] duration.
     */
    private val _isIdle = MutableStateFlow(false)

    /**
     * A read-only view of the idle state exposed as a [StateFlow].
     * Other components can observe this to react when the user becomes idle.
     */
    val isIdle: StateFlow<Boolean> = _isIdle

    /**
     * Holds the reference to the currently active polling job.
     * If non-null, it indicates that the idle detection polling is currently running.
     */
    private var job: Job? = null

    /**
     * A dedicated [CoroutineScope] for running the idle detection polling loop.
     * This scope uses the main thread dispatcher ([Dispatchers.Main]) and combines it with a
     * [SupervisorJob] to prevent cancellation of sibling coroutines if one fails.
     *
     * **Note:** The scope is not cancelled automatically by calling [stop]. If you plan to create
     * many instances of [IdleDetector] over time, ensure to cancel the scope when it's no longer needed.
     */
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /**
     * Starts the idle detection process.
     *
     * This method cancels any existing polling job before launching a new one.
     * The polling job runs in a loop while active, checking at every [pollingInterval] whether
     * the elapsed time since the last interaction exceeds [timeout]. If so, the idle state is set to `true`.
     *
     * **Usage:** Call this method when the detector should start monitoring for user inactivity.
     */
    fun start() {
        // Cancel any existing polling job to avoid multiple concurrent jobs.
        job?.cancel()
        job = scope.launch {
            // Continuously poll while the coroutine is active.
            while (isActive) {
                val elapsed = System.currentTimeMillis() - lastInteraction
                // Update the idle state: true if elapsed time exceeds the timeout.
                _isIdle.value = elapsed >= timeout.inWholeMilliseconds
                // Wait for the specified polling interval before checking again.
                delay(pollingInterval.inWholeMilliseconds)
            }
        }
    }

    /**
     * Stops the idle detection process.
     *
     * This method cancels the current polling job (if any) and resets the idle state to `false`.
     * It should be called when the idle detection is no longer required (for example, during
     * a lifecycle pause event).
     */
    fun stop() {
        job?.cancel()
        _isIdle.value = false
    }

    /**
     * Registers a user interaction.
     *
     * Updates [lastInteraction] to the current system time. If the detector is in an idle state,
     * it resets the idle flag to `false` since an interaction indicates that the user is active.
     *
     * **Usage:** This method should be called whenever a user interaction (e.g., a tap or swipe)
     * is detected.
     */
    fun registerInteraction() {
        lastInteraction = System.currentTimeMillis()
        if (_isIdle.value) _isIdle.value = false
    }
}
