package ke.co.banit.idle_detector_compose

import android.util.Log

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
 * Configurable logging infrastructure for Idle Detector Compose library.
 *
 * This object provides centralized logging control for the entire library,
 * allowing consumers to enable/disable logging and customize the log tag.
 *
 * ## Basic Usage
 * ```kotlin
 * // Enable logging for debugging
 * IdleDetectorLogger.isEnabled = true
 *
 * // Customize log tag
 * IdleDetectorLogger.tag = "MyApp:Idle"
 * ```
 *
 * ## Production Usage
 * ```kotlin
 * // Disable logging in release builds
 * IdleDetectorLogger.isEnabled = BuildConfig.DEBUG
 * ```
 *
 * By default, logging is disabled to avoid unnecessary LogCat noise in production.
 * Enable it during development or debugging to track idle detection behavior.
 *
 * @property isEnabled Controls whether logging is active. Default is false.
 * @property tag The tag used for all log messages. Default is "IdleDetector".
 */
object IdleDetectorLogger {
    /**
     * Enables or disables logging for the entire Idle Detector library.
     *
     * When set to false, all log calls become no-ops with minimal performance impact.
     * Set to true to see detailed logging of idle detection lifecycle, state changes,
     * and interactions.
     *
     * **Default:** `false` (logging disabled)
     *
     * ## Example
     * ```kotlin
     * // Enable for debug builds only
     * IdleDetectorLogger.isEnabled = BuildConfig.DEBUG
     *
     * // Or enable unconditionally for troubleshooting
     * IdleDetectorLogger.isEnabled = true
     * ```
     */
    var isEnabled: Boolean = false

    /**
     * The tag used for all log messages from the library.
     *
     * Customize this to make library logs easily filterable in LogCat,
     * especially useful when integrating with multiple libraries or debugging
     * specific app flows.
     *
     * **Default:** `"IdleDetector"`
     *
     * ## Example
     * ```kotlin
     * IdleDetectorLogger.tag = "MyApp:IdleDetection"
     * // Now all logs will appear with tag "MyApp:IdleDetection"
     * ```
     */
    var tag: String = "IdleDetector"

    /**
     * Logs a debug message if logging is enabled.
     *
     * Debug messages are used for detailed flow tracking and state changes.
     * These messages help understand the idle detection lifecycle during development.
     *
     * @param message The message to log
     */
    fun d(message: String) {
        if (isEnabled) {
            Log.d(tag, message)
        }
    }

    /**
     * Logs an error message if logging is enabled.
     *
     * Error messages indicate issues or unexpected conditions that occurred
     * during idle detection. These are always worth investigating.
     *
     * @param message The error message to log
     * @param throwable Optional exception/throwable associated with the error
     */
    fun e(message: String, throwable: Throwable? = null) {
        if (isEnabled) {
            if (throwable != null) {
                Log.e(tag, message, throwable)
            } else {
                Log.e(tag, message)
            }
        }
    }

    /**
     * Logs a warning message if logging is enabled.
     *
     * Warning messages indicate conditions that are not errors but may need attention,
     * such as unusual timing values or edge cases.
     *
     * @param message The warning message to log
     */
    fun w(message: String) {
        if (isEnabled) {
            Log.w(tag, message)
        }
    }

    /**
     * Logs an info message if logging is enabled.
     *
     * Info messages provide high-level information about idle detection events,
     * such as when idle state changes or important lifecycle events occur.
     *
     * @param message The info message to log
     */
    fun i(message: String) {
        if (isEnabled) {
            Log.i(tag, message)
        }
    }
}
