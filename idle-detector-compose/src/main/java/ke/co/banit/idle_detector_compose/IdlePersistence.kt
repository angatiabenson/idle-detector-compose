package ke.co.banit.idle_detector_compose

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import ke.co.banit.idle_detector_compose.IdlePersistence.PREFS_NAME

/**
 * ------------------------------------------------------------------------
 * Project: idle-detector-app
 * File Created by: Angatia Benson on Mon, Apr 07, 2025
 * ------------------------------------------------------------------------
 * © 2025 CoreTec Solution Africa. All rights reserved.
 * ------------------------------------------------------------------------
 * This file is part of the CoreTec Solution Africa project and is intended
 * for internal use within the company. Unauthorized copying, distribution,
 * or use of this file, via any medium, is strictly prohibited.
 * ------------------------------------------------------------------------
 **/

/**
 * IdlePersistence is a singleton object responsible for storing and retrieving data
 * related to the idle detection mechanism using SharedPreferences.
 *
 * It manages:
 * - The last recorded interaction timestamp.
 * - A flag indicating if a background timeout was triggered.
 *
 * This persistence layer is used for coordinating between foreground and background idle checks.
 */
internal object IdlePersistence {
    // Name of the SharedPreferences file used to persist idle detector data.
    private const val PREFS_NAME = "IdleDetectorPrefs"

    // Key for storing the last active timestamp (in milliseconds).
    private const val KEY_LAST_ACTIVE_TIMESTAMP = "lastActiveTimestampMillis"

    // Key for storing the flag that indicates whether a background timeout has been triggered.
    private const val KEY_BACKGROUND_TIMEOUT_TRIGGERED = "backgroundTimeoutTriggered" // Keep this for worker communication

    /**
     * Retrieves the SharedPreferences instance for idle detector persistence.
     *
     * @param context The context used to access SharedPreferences.
     * @return A SharedPreferences instance with the name defined by [PREFS_NAME].
     */
    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Records a user interaction by saving the provided timestamp.
     *
     * This function also resets the background timeout triggered flag to false.
     *
     * @param context The context used to access SharedPreferences.
     * @param timestamp The timestamp (in milliseconds) of the recorded interaction.
     */
    fun recordInteraction(context: Context, timestamp: Long) {
        //Log.d(TAG, "Recording interaction time: $timestamp")
        getPrefs(context).edit {
            putLong(KEY_LAST_ACTIVE_TIMESTAMP, timestamp)
                .putBoolean(KEY_BACKGROUND_TIMEOUT_TRIGGERED, false) // Reset flag on new interaction
        }
    }

    /**
     * Retrieves the last recorded interaction timestamp.
     *
     * @param context The context used to access SharedPreferences.
     * @return The last interaction timestamp in milliseconds, or 0L if no timestamp is stored.
     */
    fun getLastInteractionTimestamp(context: Context): Long {
        val timestamp = getPrefs(context).getLong(KEY_LAST_ACTIVE_TIMESTAMP, 0L)
        //Log.d(TAG, "Retrieved last interaction time: $timestamp")
        return timestamp
    }

    /**
     * Sets the background timeout triggered flag.
     *
     * This flag is used to indicate that the background idle timeout has been reached.
     *
     * @param context The context used to access SharedPreferences.
     * @param triggered A Boolean indicating whether the background timeout was triggered.
     */
    fun setBackgroundTimeoutTriggered(context: Context, triggered: Boolean) {
        //Log.d(TAG, "Setting background timeout triggered flag: $triggered")
        getPrefs(context).edit { putBoolean(KEY_BACKGROUND_TIMEOUT_TRIGGERED, triggered) }
    }

    /**
     * Checks whether the background timeout flag has been triggered.
     *
     * @param context The context used to access SharedPreferences.
     * @return True if the background timeout was triggered, false otherwise.
     */
    fun isBackgroundTimeoutTriggered(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_BACKGROUND_TIMEOUT_TRIGGERED, false)
    }

    /**
     * Resets the persisted idle detection data.
     *
     * This clears both the last interaction timestamp and the background timeout triggered flag.
     *
     * @param context The context used to access SharedPreferences.
     */
    fun reset(context: Context) {
        getPrefs(context).edit {
            remove(KEY_LAST_ACTIVE_TIMESTAMP)
                .remove(KEY_BACKGROUND_TIMEOUT_TRIGGERED)
        }
    }
}

