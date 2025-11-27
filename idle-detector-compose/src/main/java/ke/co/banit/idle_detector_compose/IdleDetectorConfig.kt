package ke.co.banit.idle_detector_compose

import androidx.compose.runtime.Immutable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

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
 * Configuration for idle detection behavior.
 *
 * This immutable data class encapsulates all configuration options for the Idle Detector,
 * providing a type-safe and organized way to customize idle detection behavior.
 *
 * ## Basic Usage
 * ```kotlin
 * val config = IdleDetectorConfig.Builder()
 *     .timeout(5.minutes)
 *     .checkInterval(2.seconds)
 *     .enableLogging()
 *     .build()
 *
 * IdleDetectorProvider(config = config) {
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
 *     .persistenceDebounce(1.seconds)
 *     .build()
 * ```
 *
 * @property timeout Duration of inactivity before the app is considered idle
 * @property checkInterval How often to poll for idle state while app is in foreground
 * @property enableBackgroundDetection Whether to detect idle state when app is backgrounded
 * @property enableLogging Whether to enable debug logging for idle detection
 * @property debounceInteractions Whether to debounce rapid user interactions
 * @property interactionDebounceWindow Time window for interaction debouncing
 * @property persistenceDebounceWindow Time window for persistence write debouncing
 *
 * @see IdleDetectorConfig.Builder
 * @see IdleDetectorProvider
 */
@Immutable
data class IdleDetectorConfig(
    val timeout: Duration,
    val checkInterval: Duration = 1.seconds,
    val enableBackgroundDetection: Boolean = true,
    val enableLogging: Boolean = false,
    val debounceInteractions: Boolean = true,
    val interactionDebounceWindow: Duration = 100.milliseconds,
    val persistenceDebounceWindow: Duration = 500.milliseconds,
) {
    init {
        require(timeout.inWholeMilliseconds > 0) {
            "Timeout must be positive, got: $timeout"
        }
        require(checkInterval.inWholeMilliseconds > 0) {
            "Check interval must be positive, got: $checkInterval"
        }
        require(timeout > checkInterval) {
            "Timeout ($timeout) must be greater than check interval ($checkInterval)"
        }
        require(interactionDebounceWindow.inWholeMilliseconds >= 0) {
            "Interaction debounce window must be non-negative, got: $interactionDebounceWindow"
        }
        require(persistenceDebounceWindow.inWholeMilliseconds >= 0) {
            "Persistence debounce window must be non-negative, got: $persistenceDebounceWindow"
        }
    }

    /**
     * Builder for creating [IdleDetectorConfig] instances with a fluent API.
     *
     * Provides a type-safe and readable way to configure idle detection behavior
     * with sensible defaults for all optional parameters.
     *
     * ## Example
     * ```kotlin
     * val config = IdleDetectorConfig.Builder()
     *     .timeout(5.minutes)              // Required
     *     .checkInterval(2.seconds)         // Optional
     *     .enableLogging()                  // Optional
     *     .disableBackgroundDetection()     // Optional
     *     .build()
     * ```
     */
    class Builder {
        private var timeout: Duration? = null
        private var checkInterval: Duration = 1.seconds
        private var enableBackgroundDetection: Boolean = true
        private var enableLogging: Boolean = false
        private var debounceInteractions: Boolean = true
        private var interactionDebounceWindow: Duration = 100.milliseconds
        private var persistenceDebounceWindow: Duration = 500.milliseconds

        /**
         * Sets the idle timeout duration.
         *
         * This is the duration of inactivity after which the app is considered idle.
         * Must be greater than the check interval.
         *
         * **Required parameter** - must be called before [build].
         *
         * ## Examples
         * ```kotlin
         * .timeout(30.seconds)    // Quick timeout for testing
         * .timeout(5.minutes)     // Standard session timeout
         * .timeout(15.minutes)    // Extended session timeout
         * ```
         *
         * @param duration The timeout duration (must be positive)
         * @return This builder instance for chaining
         */
        fun timeout(duration: Duration) = apply {
            timeout = duration
        }

        /**
         * Sets the polling interval for checking idle state.
         *
         * This determines how often the library checks if the timeout has been exceeded
         * while the app is in the foreground. Smaller values provide more responsive
         * detection but use more CPU/battery.
         *
         * **Default:** 1 second
         *
         * ## Recommendations
         * - For timeouts < 1 minute: Use 500ms - 1s
         * - For timeouts 1-5 minutes: Use 1-2s
         * - For timeouts > 5 minutes: Use 2-5s
         *
         * @param duration The check interval (must be positive and less than timeout)
         * @return This builder instance for chaining
         */
        fun checkInterval(duration: Duration) = apply {
            checkInterval = duration
        }

        /**
         * Enables background idle detection.
         *
         * When enabled, the library schedules a WorkManager job when the app is backgrounded
         * to detect if the idle timeout is exceeded while the app is paused.
         *
         * **Default:** Enabled
         *
         * @return This builder instance for chaining
         * @see disableBackgroundDetection
         */
        fun enableBackgroundDetection() = apply {
            enableBackgroundDetection = true
        }

        /**
         * Disables background idle detection.
         *
         * When disabled, idle detection only occurs while the app is in the foreground.
         * Use this if you don't need to track idle time while the app is backgrounded,
         * which can save battery and WorkManager scheduling overhead.
         *
         * **Default:** Enabled (use this to disable)
         *
         * @return This builder instance for chaining
         * @see enableBackgroundDetection
         */
        fun disableBackgroundDetection() = apply {
            enableBackgroundDetection = false
        }

        /**
         * Enables debug logging for idle detection.
         *
         * When enabled, the library will log detailed information about:
         * - Lifecycle events (create, resume, pause, destroy)
         * - User interactions
         * - Idle state changes
         * - Background worker scheduling
         *
         * **Default:** Disabled
         *
         * ## Example
         * ```kotlin
         * // Enable for debug builds only
         * if (BuildConfig.DEBUG) {
         *     builder.enableLogging()
         * }
         * ```
         *
         * @return This builder instance for chaining
         * @see IdleDetectorLogger
         */
        fun enableLogging() = apply {
            enableLogging = true
        }

        /**
         * Enables interaction debouncing (default behavior).
         *
         * When enabled, rapid user interactions (within [interactionDebounceWindow])
         * are coalesced into a single interaction event, reducing function call overhead.
         *
         * **Default:** Enabled
         *
         * @return This builder instance for chaining
         * @see disableInteractionDebounce
         * @see interactionDebounce
         */
        fun enableInteractionDebounce() = apply {
            debounceInteractions = true
        }

        /**
         * Disables interaction debouncing.
         *
         * When disabled, every single touch and keyboard event immediately triggers
         * interaction registration. This provides the most accurate interaction tracking
         * but increases CPU usage and function call overhead.
         *
         * **Default:** Enabled (use this to disable)
         *
         * @return This builder instance for chaining
         * @see enableInteractionDebounce
         */
        fun disableInteractionDebounce() = apply {
            debounceInteractions = false
        }

        /**
         * Sets the interaction debouncing window.
         *
         * Interactions occurring within this time window after the last interaction
         * are ignored to reduce overhead. Only has effect if interaction debouncing
         * is enabled.
         *
         * **Default:** 100 milliseconds
         *
         * ## Recommendations
         * - For normal use: 50-100ms
         * - For high-frequency interactions (games, drawing): 16-50ms
         * - For low-frequency interactions (reading): 200-500ms
         *
         * @param duration The debounce window (must be non-negative)
         * @return This builder instance for chaining
         * @see enableInteractionDebounce
         */
        fun interactionDebounce(duration: Duration) = apply {
            interactionDebounceWindow = duration
        }

        /**
         * Sets the persistence debouncing window.
         *
         * Writes to SharedPreferences within this time window are debounced to reduce
         * disk I/O. The latest timestamp is always persisted when the window expires
         * or when the app is paused.
         *
         * **Default:** 500 milliseconds
         *
         * ## Recommendations
         * - For normal use: 500ms - 1s
         * - For critical persistence: 100-200ms
         * - For relaxed persistence: 1-2s
         *
         * @param duration The debounce window (must be non-negative)
         * @return This builder instance for chaining
         */
        fun persistenceDebounce(duration: Duration) = apply {
            persistenceDebounceWindow = duration
        }

        /**
         * Builds the [IdleDetectorConfig] instance.
         *
         * Validates all parameters and creates an immutable configuration object.
         *
         * @return A validated [IdleDetectorConfig] instance
         * @throws IllegalArgumentException if timeout is not set or validation fails
         */
        fun build(): IdleDetectorConfig {
            val finalTimeout = requireNotNull(timeout) {
                "Timeout is required. Call timeout(duration) before build()"
            }

            return IdleDetectorConfig(
                timeout = finalTimeout,
                checkInterval = checkInterval,
                enableBackgroundDetection = enableBackgroundDetection,
                enableLogging = enableLogging,
                debounceInteractions = debounceInteractions,
                interactionDebounceWindow = interactionDebounceWindow,
                persistenceDebounceWindow = persistenceDebounceWindow,
            )
        }
    }

    companion object {
        /**
         * Creates a configuration builder.
         *
         * Convenience method for creating a [Builder] instance.
         *
         * ## Example
         * ```kotlin
         * val config = IdleDetectorConfig.builder()
         *     .timeout(5.minutes)
         *     .build()
         * ```
         *
         * @return A new [Builder] instance
         */
        fun builder(): Builder = Builder()
    }
}
