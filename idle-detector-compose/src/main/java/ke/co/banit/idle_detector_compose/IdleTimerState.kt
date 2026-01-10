package ke.co.banit.idle_detector_compose

import androidx.compose.runtime.Immutable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO

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
 * Represents the current state of the idle detection timer.
 *
 * This immutable data class provides real-time information about the countdown
 * to idle state, enabling developers to create custom countdown UIs, progress
 * indicators, and warning displays.
 *
 * ## Usage
 * ```kotlin
 * val timerState by LocalIdleTimerState.current
 *
 * // Display remaining time
 * Text("Session expires in: ${timerState.remainingTime.inWholeSeconds}s")
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
 * @property remainingTime Time remaining until idle timeout (never negative)
 * @property totalTimeout The configured idle timeout duration
 * @property warningThreshold Optional duration before timeout when warning period starts
 * @property isIdle Whether the idle timeout has been reached
 * @property isInWarningPeriod Whether currently in the warning period before idle
 * @property progress Normalized progress from 0.0 (just interacted) to 1.0 (idle)
 *
 * @see LocalIdleTimerState
 * @see IdleDetectorConfig
 */
@Immutable
data class IdleTimerState(
    val remainingTime: Duration,
    val totalTimeout: Duration,
    val warningThreshold: Duration? = null,
    val isIdle: Boolean = false,
    val isInWarningPeriod: Boolean = false,
) {
    /**
     * Progress from 0.0 (just interacted) to 1.0 (idle timeout reached).
     *
     * This value is clamped between 0.0 and 1.0 and is suitable for use
     * with progress indicators like LinearProgressIndicator or CircularProgressIndicator.
     *
     * ## Example
     * ```kotlin
     * LinearProgressIndicator(
     *     progress = timerState.progress,
     *     color = if (timerState.isInWarningPeriod) Color.Red else Color.Green
     * )
     * ```
     */
    val progress: Float
        get() {
            if (totalTimeout.inWholeMilliseconds <= 0) return 0f
            val elapsed = totalTimeout - remainingTime
            return (elapsed.inWholeMilliseconds.toFloat() / totalTimeout.inWholeMilliseconds)
                .coerceIn(0f, 1f)
        }

    /**
     * Remaining time formatted as "MM:SS" string.
     *
     * Useful for displaying countdown timers in UI.
     *
     * ## Example
     * ```kotlin
     * Text("Time remaining: ${timerState.formattedRemainingTime}")
     * // Output: "Time remaining: 04:30"
     * ```
     */
    val formattedRemainingTime: String
        get() {
            val totalSeconds = remainingTime.inWholeSeconds.coerceAtLeast(0)
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return "%02d:%02d".format(minutes, seconds)
        }

    /**
     * Remaining time formatted as "HH:MM:SS" string for longer durations.
     *
     * Includes hours when the remaining time is 1 hour or more.
     *
     * ## Example
     * ```kotlin
     * Text("Session expires in: ${timerState.formattedRemainingTimeLong}")
     * // Output: "Session expires in: 01:04:30"
     * ```
     */
    val formattedRemainingTimeLong: String
        get() {
            val totalSeconds = remainingTime.inWholeSeconds.coerceAtLeast(0)
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            return if (hours > 0) {
                "%02d:%02d:%02d".format(hours, minutes, seconds)
            } else {
                "%02d:%02d".format(minutes, seconds)
            }
        }

    /**
     * The elapsed time since the last interaction.
     *
     * ## Example
     * ```kotlin
     * val elapsed = timerState.elapsedTime
     * Log.d("IdleTimer", "User inactive for ${elapsed.inWholeSeconds} seconds")
     * ```
     */
    val elapsedTime: Duration
        get() = (totalTimeout - remainingTime).coerceAtLeast(ZERO)

    /**
     * Time remaining until the warning period starts.
     *
     * Returns null if no warning threshold is configured, or [Duration.ZERO]
     * if already in the warning period.
     *
     * ## Example
     * ```kotlin
     * timerState.timeUntilWarning?.let { timeUntil ->
     *     Log.d("IdleTimer", "Warning will show in ${timeUntil.inWholeSeconds}s")
     * }
     * ```
     */
    val timeUntilWarning: Duration?
        get() {
            val threshold = warningThreshold ?: return null
            val timeUntil = remainingTime - threshold
            return timeUntil.coerceAtLeast(ZERO)
        }

    /**
     * Progress within the warning period from 0.0 (warning just started) to 1.0 (idle).
     *
     * Returns null if no warning threshold is configured or not yet in warning period.
     * Useful for creating warning-specific progress indicators.
     *
     * ## Example
     * ```kotlin
     * timerState.warningProgress?.let { progress ->
     *     CircularProgressIndicator(
     *         progress = progress,
     *         color = Color.Red
     *     )
     * }
     * ```
     */
    val warningProgress: Float?
        get() {
            val threshold = warningThreshold ?: return null
            if (!isInWarningPeriod) return null
            if (threshold.inWholeMilliseconds <= 0) return 1f
            val elapsed = threshold - remainingTime
            return (elapsed.inWholeMilliseconds.toFloat() / threshold.inWholeMilliseconds)
                .coerceIn(0f, 1f)
        }

    companion object {
        /**
         * Creates an initial timer state with full remaining time.
         *
         * @param timeout The total idle timeout duration
         * @param warningThreshold Optional warning threshold duration
         * @return A new [IdleTimerState] with maximum remaining time
         */
        fun initial(
            timeout: Duration,
            warningThreshold: Duration? = null,
        ): IdleTimerState = IdleTimerState(
            remainingTime = timeout,
            totalTimeout = timeout,
            warningThreshold = warningThreshold,
            isIdle = false,
            isInWarningPeriod = false,
        )

        /**
         * Creates a timer state representing the idle condition.
         *
         * @param timeout The total idle timeout duration
         * @param warningThreshold Optional warning threshold duration
         * @return A new [IdleTimerState] with zero remaining time and isIdle = true
         */
        fun idle(
            timeout: Duration,
            warningThreshold: Duration? = null,
        ): IdleTimerState = IdleTimerState(
            remainingTime = ZERO,
            totalTimeout = timeout,
            warningThreshold = warningThreshold,
            isIdle = true,
            isInWarningPeriod = warningThreshold != null,
        )

        /**
         * Creates a timer state from elapsed time since last interaction.
         *
         * This is the primary factory method used internally by the idle detector
         * to create timer states based on the actual elapsed time.
         *
         * @param elapsedTime Time elapsed since last user interaction
         * @param timeout The total idle timeout duration
         * @param warningThreshold Optional warning threshold duration
         * @return A new [IdleTimerState] calculated from the elapsed time
         */
        fun fromElapsed(
            elapsedTime: Duration,
            timeout: Duration,
            warningThreshold: Duration? = null,
        ): IdleTimerState {
            val remaining = (timeout - elapsedTime).coerceAtLeast(ZERO)
            val isIdle = remaining <= ZERO
            val isWarning = warningThreshold?.let { threshold ->
                remaining <= threshold && !isIdle
            } ?: false

            return IdleTimerState(
                remainingTime = remaining,
                totalTimeout = timeout,
                warningThreshold = warningThreshold,
                isIdle = isIdle,
                isInWarningPeriod = isWarning,
            )
        }
    }
}

/**
 * Extension function to check if timer is close to idle state.
 *
 * @param threshold Duration threshold to check against
 * @return true if remaining time is less than or equal to threshold
 */
fun IdleTimerState.isCloseTo(threshold: Duration): Boolean {
    return remainingTime <= threshold && !isIdle
}

/**
 * Extension function to get remaining time as a percentage.
 *
 * @return Percentage of time remaining (0-100)
 */
val IdleTimerState.remainingPercentage: Int
    get() = ((1f - progress) * 100).toInt().coerceIn(0, 100)
