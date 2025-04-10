package ke.co.banit.idle_detector_compose

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
 * IdleDetector is responsible for monitoring user activity to determine whether the application is idle.
 * It periodically polls the source-of-truth timestamp for the last user interaction and compares it against
 * a configured timeout. The result (idle or not) is exposed as a read-only StateFlow.
 *
 * @param timeout Duration defining how long the application must be inactive to be considered idle.
 * @param pollingInterval Duration that specifies how frequently to check the idle state.
 * @param getLastInteractionTimestamp Function that returns the timestamp of the last user interaction.
 */
internal class IdleDetector(
    private val timeout: Duration,
    private val pollingInterval: Duration,
    private val getLastInteractionTimestamp: () -> Long, // Function to get the source-of-truth timestamp
) {
    // Internal mutable state representing whether the app is idle.
    private val _isIdle = MutableStateFlow(false)
    // Publicly exposed read-only state flow for observing idle state.
    val isIdle: StateFlow<Boolean> = _isIdle.asStateFlow()

    // Job used for the polling coroutine. Helps control the polling lifecycle.
    private var pollingJob: Job? = null

    // Create a CoroutineScope using the Main dispatcher with a SupervisorJob so that failures in one child
    // do not cancel the entire scope. The scope is also named for easier debugging.
    private val scope =
        CoroutineScope(Dispatchers.Main + SupervisorJob() + CoroutineName("IdleDetectorScope"))

    /**
     * Starts the polling process to monitor the idle state.
     *
     * If polling is already active, it logs a message and exits.
     * It first performs an immediate idle state check and then launches a coroutine that repeatedly checks
     * the idle state at intervals defined by [pollingInterval].
     */
    fun startPolling() {
        // Check if a polling job is already running to avoid duplicates.
        if (pollingJob?.isActive == true) {
            //Log.d(TAG, "Polling already active.")
            return
        }
        // Stop any existing polling before starting a new one.
        stopPolling()
        //Log.d( TAG, "Starting foreground polling. Timeout: ${timeout.inWholeMilliseconds}ms, Interval: ${pollingInterval.inWholeMilliseconds}ms" )

        // Perform an immediate check of the idle state.
        checkAndUpdateIdleState()

        // Launch a coroutine to periodically check the idle state.
        pollingJob = scope.launch {
            while (isActive) {
                delay(pollingInterval.inWholeMilliseconds)
                checkAndUpdateIdleState()
            }
        }
        // Log completion or cancellation of the polling job for debugging.
        pollingJob?.invokeOnCompletion { throwable ->
            //Log.d(TAG, "Polling job completed. Reason: $throwable")
        }
    }

    /**
     * Checks and updates the idle state based on the elapsed time since the last user interaction.
     *
     * The method performs the following:
     * - Retrieves the last interaction timestamp.
     * - Calculates the elapsed time since the last interaction.
     * - Compares the elapsed time with the configured [timeout].
     * - Updates the internal idle state if a change is detected.
     */
    private fun checkAndUpdateIdleState() {
        // Get the timestamp of the last recorded user interaction.
        val lastInteraction = getLastInteractionTimestamp()
        if (lastInteraction == 0L) {
            // If no interaction is recorded yet, assume the app is active.
            // Alternatively, you could set idle state here based on different logic.
            if (_isIdle.value) {
                //Log.d(TAG, "checkAndUpdateIdleState: No interaction time, setting idle state to false.")
                _isIdle.value = false
            }
            return
        }

        // Compute the elapsed time since the last interaction.
        val elapsed = System.currentTimeMillis() - lastInteraction
        // Determine if the elapsed time meets or exceeds the idle timeout threshold.
        val currentlyIdle = elapsed >= timeout.inWholeMilliseconds
        //Log.d( TAG, "Polling Check: Now=${System.currentTimeMillis()}, LastInteraction=$lastInteraction, " + "Elapsed=$elapsed, Timeout=${timeout.inWholeMilliseconds}, IsIdle=$currentlyIdle" )

        // Update the idle state only if it has changed to avoid redundant emissions.
        if (_isIdle.value != currentlyIdle) {
            //Log.d(TAG, "checkAndUpdateIdleState: Idle state changing to $currentlyIdle (Elapsed: ${elapsed}ms)")
            _isIdle.value = currentlyIdle
        }
    }

    /**
     * Reports that a user interaction has occurred.
     *
     * This method should be called externally when an interaction happens to immediately reset the idle state to active.
     * It does not update the interaction timestamp; updating is handled by the external source.
     */
    fun reportInteractionOccurred() {
        //Log.d(TAG, "Interaction reported. Setting idle state to false.")
        if (_isIdle.value) {
            _isIdle.value = false
        }
        // Note: The timestamp update is managed externally.
    }

    /**
     * Stops the polling process by cancelling the polling coroutine if it is active.
     *
     * This prevents further idle state checks. The idle state is not reset here because it depends on the persisted interaction time.
     */
    fun stopPolling() {
        if (pollingJob?.isActive == true) {
            //Log.d(TAG, "Stopping foreground polling.")
            pollingJob?.cancel()
        }
        pollingJob = null
    }

    /**
     * Cleans up the coroutine scope used for idle detection.
     *
     * This should be called when the IdleDetector is no longer needed to avoid memory leaks.
     */
    fun cleanupScope() {
        //Log.d(TAG, "Cleaning up IdleDetector coroutine scope.")
        scope.cancel()
    }
}
