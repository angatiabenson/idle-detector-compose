package ke.co.banit.idle_detector_compose

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

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
 * A background worker that checks if the application has been idle for a specified timeout period.
 *
 * This worker is designed to be used as part of an idle detection system. It retrieves the last user interaction timestamp,
 * compares it against the current system time, and determines whether the idle timeout threshold has been met. If the condition is met,
 * it sets a flag to indicate that a background timeout has been triggered.
 *
 * The worker executes its logic on the IO dispatcher to ensure non-blocking background execution.
 *
 * Usage:
 * - Schedule this worker via WorkManager.
 * - Pass the desired timeout in milliseconds using the KEY_TIMEOUT_MILLIS input data key.
 */
internal class BackgroundTimeoutWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        /** The unique name for this worker. */
        const val WORK_NAME = "IdleDetectorBackgroundTimeoutWorker"

        /** Input data key for specifying the timeout value in milliseconds. */
        const val KEY_TIMEOUT_MILLIS = "timeoutMillis"

        /** Default timeout in minutes used as a fallback when no timeout is provided. */
        private const val DEFAULT_TIMEOUT_MINUTES = 15L // Fallback
    }

    /**
     * The main work function that is executed when the worker is run.
     *
     * It performs the following steps:
     * 1. Retrieves the timeout value from the input data (or uses a default fallback).
     * 2. Obtains the last interaction timestamp from persistent storage.
     * 3. Compares the current system time with the last interaction time to determine if the idle timeout threshold has been reached.
     * 4. Logs the execution details and updates the idle state flag accordingly.
     *
     * @return Result.success() indicating the work completed successfully.
     */
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        // Retrieve the timeout value from input data; fallback to default timeout if not provided.
        val timeoutMillis = inputData.getLong(
            KEY_TIMEOUT_MILLIS,
            TimeUnit.MINUTES.toMillis(DEFAULT_TIMEOUT_MINUTES)
        )

        // Retrieve the timestamp of the last user interaction from persistent storage.
        val lastActiveTimestamp = IdlePersistence.getLastInteractionTimestamp(applicationContext)

        // Get the current system time.
        val currentTime = System.currentTimeMillis()

        // Determine if the idle condition is met:
        // - A valid last interaction timestamp exists.
        // - The difference between current time and last interaction exceeds the timeout.
        val conditionMet = lastActiveTimestamp > 0 && (currentTime - lastActiveTimestamp >= timeoutMillis)

        // Log the current state of the worker for debugging purposes.
        IdleDetectorLogger.d("Worker running: Now=${currentTime}, LastInteraction=${lastActiveTimestamp}, Timeout=${timeoutMillis}ms, ConditionMet=$conditionMet")

        // Set the background timeout flag based on whether the idle condition has been met.
        if (conditionMet) {
            IdleDetectorLogger.d("Background timeout threshold reached. Setting flag.")
            IdlePersistence.setBackgroundTimeoutTriggered(applicationContext, true)
        } else {
            IdleDetectorLogger.d("Background timeout threshold NOT reached.")
            IdlePersistence.setBackgroundTimeoutTriggered(applicationContext, false)
        }

        // Indicate that the work completed successfully.
        return@withContext Result.success()
    }
}
