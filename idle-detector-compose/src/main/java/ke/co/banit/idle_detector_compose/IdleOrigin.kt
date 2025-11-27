package ke.co.banit.idle_detector_compose

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
 * Represents the origin of an idle state detection.
 *
 * This sealed interface provides type-safe representation of where idle detection
 * was triggered, replacing the boolean flag for better code clarity and type safety.
 *
 * ## Usage
 * ```kotlin
 * IdleDetectorProvider(
 *     idleTimeout = 5.minutes,
 *     onIdleWithOrigin = { origin ->
 *         when (origin) {
 *             IdleOrigin.Foreground -> {
 *                 // User became idle while app was active
 *                 showWarningDialog()
 *             }
 *             IdleOrigin.Background -> {
 *                 // User returned after being idle in background
 *                 logoutUser()
 *             }
 *         }
 *     }
 * )
 * ```
 *
 * @see IdleDetectorProvider
 */
sealed interface IdleOrigin {
    /**
     * Idle state was detected while the app was in the foreground.
     *
     * This typically occurs when the user stops interacting with the app
     * but keeps it visible on screen.
     */
    data object Foreground : IdleOrigin

    /**
     * Idle state was detected after the app returned from background.
     *
     * This occurs when the user backgrounds the app (e.g., switches to another app
     * or locks the screen) and the idle timeout expires while the app is paused.
     * The detection is triggered when the app resumes.
     */
    data object Background : IdleOrigin

    companion object {
        /**
         * Creates an [IdleOrigin] from a boolean flag for backward compatibility.
         *
         * @param fromBackground true if idle was detected in background, false otherwise
         * @return [Background] if [fromBackground] is true, [Foreground] otherwise
         */
        fun fromBoolean(fromBackground: Boolean): IdleOrigin {
            return if (fromBackground) Background else Foreground
        }
    }
}

/**
 * Extension property to convert [IdleOrigin] back to boolean for backward compatibility.
 *
 * @return true if this is [IdleOrigin.Background], false if [IdleOrigin.Foreground]
 */
val IdleOrigin.isBackground: Boolean
    get() = this is IdleOrigin.Background

/**
 * Extension property to check if idle originated from foreground.
 *
 * @return true if this is [IdleOrigin.Foreground], false if [IdleOrigin.Background]
 */
val IdleOrigin.isForeground: Boolean
    get() = this is IdleOrigin.Foreground
