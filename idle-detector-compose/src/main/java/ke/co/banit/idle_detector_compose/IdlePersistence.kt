package ke.co.banit.idle_detector_compose

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

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
 * IdlePersistence is a singleton object responsible for storing and retrieving data
 * related to the idle detection mechanism using SharedPreferences.
 *
 * It manages:
 * - The last recorded interaction timestamp.
 * - A flag indicating if a background timeout was triggered.
 *
 * This persistence layer is used for coordinating between foreground and background idle checks.
 *
 * ## Performance Optimization
 * To reduce disk I/O overhead, this object implements debouncing for write operations.
 * Rapid writes are coalesced, and the latest values are cached in memory.
 */
internal object IdlePersistence {
    // Name of the SharedPreferences file used to persist idle detector data.
    private const val PREFS_NAME = "IdleDetectorPrefs"

    // Key for storing the last active timestamp (in milliseconds).
    private const val KEY_LAST_ACTIVE_TIMESTAMP = "lastActiveTimestampMillis"

    // Key for storing the flag that indicates whether a background timeout has been triggered.
    private const val KEY_BACKGROUND_TIMEOUT_TRIGGERED = "backgroundTimeoutTriggered"

    // Debounce window for write operations (milliseconds)
    private const val WRITE_DEBOUNCE_MS = 500L

    // Cached timestamp value to reduce SharedPreferences reads
    @Volatile
    private var cachedTimestamp: Long = 0L

    // Cached background timeout flag
    @Volatile
    private var cachedBackgroundFlag: Boolean = false

    // Last time we actually wrote to SharedPreferences
    @Volatile
    private var lastWriteTime: Long = 0L

    // Whether cache has been initialized from SharedPreferences
    @Volatile
    private var cacheInitialized: Boolean = false

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
     * Initializes the cache from SharedPreferences if not already initialized.
     *
     * @param context The context used to access SharedPreferences.
     */
    private fun ensureCacheInitialized(context: Context) {
        if (!cacheInitialized) {
            synchronized(this) {
                if (!cacheInitialized) {
                    val prefs = getPrefs(context)
                    cachedTimestamp = prefs.getLong(KEY_LAST_ACTIVE_TIMESTAMP, 0L)
                    cachedBackgroundFlag = prefs.getBoolean(KEY_BACKGROUND_TIMEOUT_TRIGGERED, false)
                    cacheInitialized = true
                    IdleDetectorLogger.d("Persistence cache initialized: timestamp=$cachedTimestamp, backgroundFlag=$cachedBackgroundFlag")
                }
            }
        }
    }

    /**
     * Records a user interaction by saving the provided timestamp.
     *
     * This function implements debouncing to reduce disk writes. The timestamp
     * is immediately cached in memory, but only written to SharedPreferences if:
     * - [forceWrite] is true, OR
     * - More than [WRITE_DEBOUNCE_MS] milliseconds have elapsed since the last write
     *
     * The background timeout flag is always reset to false when an interaction is recorded.
     *
     * @param context The context used to access SharedPreferences.
     * @param timestamp The timestamp (in milliseconds) of the recorded interaction.
     * @param forceWrite If true, writes immediately to disk regardless of debounce window.
     */
    fun recordInteraction(context: Context, timestamp: Long, forceWrite: Boolean = false) {
        ensureCacheInitialized(context)

        // Update cache immediately
        cachedTimestamp = timestamp
        cachedBackgroundFlag = false

        // Determine if we should write to disk
        val now = System.currentTimeMillis()
        val shouldWrite = forceWrite || (now - lastWriteTime) >= WRITE_DEBOUNCE_MS

        if (shouldWrite) {
            lastWriteTime = now
            IdleDetectorLogger.d("Recording interaction time: $timestamp (written to disk)")

            getPrefs(context).edit {
                putLong(KEY_LAST_ACTIVE_TIMESTAMP, timestamp)
                    .putBoolean(KEY_BACKGROUND_TIMEOUT_TRIGGERED, false)
            }
        } else {
            IdleDetectorLogger.d("Recording interaction time: $timestamp (cached, write debounced)")
        }
    }

    /**
     * Retrieves the last recorded interaction timestamp.
     *
     * This method reads from the in-memory cache for better performance.
     * The cache is kept in sync with SharedPreferences.
     *
     * @param context The context used to access SharedPreferences.
     * @return The last interaction timestamp in milliseconds, or 0L if no timestamp is stored.
     */
    fun getLastInteractionTimestamp(context: Context): Long {
        ensureCacheInitialized(context)
        IdleDetectorLogger.d("Retrieved last interaction time: $cachedTimestamp (from cache)")
        return cachedTimestamp
    }

    /**
     * Forces an immediate write of cached values to SharedPreferences.
     *
     * This is useful when the app is about to be paused or destroyed, to ensure
     * the latest interaction state is persisted even if the debounce window hasn't
     * elapsed yet.
     *
     * @param context The context used to access SharedPreferences.
     */
    fun flush(context: Context) {
        if (!cacheInitialized || cachedTimestamp == 0L) {
            return
        }

        IdleDetectorLogger.d("Flushing cached values to SharedPreferences: timestamp=$cachedTimestamp, backgroundFlag=$cachedBackgroundFlag")

        getPrefs(context).edit {
            putLong(KEY_LAST_ACTIVE_TIMESTAMP, cachedTimestamp)
            putBoolean(KEY_BACKGROUND_TIMEOUT_TRIGGERED, cachedBackgroundFlag)
        }
        lastWriteTime = System.currentTimeMillis()
    }

    /**
     * Sets the background timeout triggered flag.
     *
     * This flag is used to indicate that the background idle timeout has been reached.
     * The value is immediately written to SharedPreferences (not debounced) as it's
     * a critical state change.
     *
     * @param context The context used to access SharedPreferences.
     * @param triggered A Boolean indicating whether the background timeout was triggered.
     */
    fun setBackgroundTimeoutTriggered(context: Context, triggered: Boolean) {
        ensureCacheInitialized(context)

        cachedBackgroundFlag = triggered
        IdleDetectorLogger.d("Setting background timeout triggered: $triggered")

        // Always write immediately - this is a critical state change
        getPrefs(context).edit { putBoolean(KEY_BACKGROUND_TIMEOUT_TRIGGERED, triggered) }
    }

    /**
     * Checks whether the background timeout flag has been triggered.
     *
     * This method reads from the in-memory cache for better performance.
     *
     * @param context The context used to access SharedPreferences.
     * @return True if the background timeout was triggered, false otherwise.
     */
    fun isBackgroundTimeoutTriggered(context: Context): Boolean {
        ensureCacheInitialized(context)
        IdleDetectorLogger.d("Checking background timeout triggered: $cachedBackgroundFlag (from cache)")
        return cachedBackgroundFlag
    }

    /**
     * Resets the persisted idle detection data.
     *
     * This clears both the last interaction timestamp and the background timeout triggered flag,
     * both in memory cache and in SharedPreferences.
     *
     * @param context The context used to access SharedPreferences.
     */
    fun reset(context: Context) {
        IdleDetectorLogger.d("Resetting persistence layer")

        // Clear cache
        cachedTimestamp = 0L
        cachedBackgroundFlag = false
        lastWriteTime = 0L
        cacheInitialized = false

        // Clear SharedPreferences
        getPrefs(context).edit {
            remove(KEY_LAST_ACTIVE_TIMESTAMP)
                .remove(KEY_BACKGROUND_TIMEOUT_TRIGGERED)
        }
    }
}

