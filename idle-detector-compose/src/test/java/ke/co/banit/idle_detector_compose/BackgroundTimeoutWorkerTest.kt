package ke.co.banit.idle_detector_compose

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for BackgroundTimeoutWorker to verify background idle detection functionality.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class BackgroundTimeoutWorkerTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        IdlePersistence.reset(context)
    }

    @After
    fun tearDown() {
        IdlePersistence.reset(context)
    }

    @Test
    fun `worker sets timeout flag when idle threshold is exceeded`() = runBlocking {
        // Set up a last interaction timestamp that's old enough to trigger timeout
        val timeoutMillis = 5000L // 5 seconds
        val lastInteractionTime = System.currentTimeMillis() - timeoutMillis - 1000L // 1 second past timeout
        IdlePersistence.recordInteraction(context, lastInteractionTime)

        // Create worker with timeout configuration
        val inputData = workDataOf(BackgroundTimeoutWorker.KEY_TIMEOUT_MILLIS to timeoutMillis)
        val worker = TestListenableWorkerBuilder<BackgroundTimeoutWorker>(context)
            .setInputData(inputData)
            .build()

        // Execute worker
        val result = worker.doWork()

        // Verify worker completed successfully
        assertEquals(ListenableWorker.Result.success(), result)

        // Verify timeout flag was set
        assertTrue(IdlePersistence.isBackgroundTimeoutTriggered(context))
    }

    @Test
    fun `worker does not set timeout flag when within idle threshold`() = runBlocking {
        // Set up a recent interaction timestamp
        val timeoutMillis = 5000L // 5 seconds
        val lastInteractionTime = System.currentTimeMillis() - 1000L // 1 second ago (within threshold)
        IdlePersistence.recordInteraction(context, lastInteractionTime)

        // Create worker with timeout configuration
        val inputData = workDataOf(BackgroundTimeoutWorker.KEY_TIMEOUT_MILLIS to timeoutMillis)
        val worker = TestListenableWorkerBuilder<BackgroundTimeoutWorker>(context)
            .setInputData(inputData)
            .build()

        // Execute worker
        val result = worker.doWork()

        // Verify worker completed successfully
        assertEquals(ListenableWorker.Result.success(), result)

        // Verify timeout flag was not set
        assertFalse(IdlePersistence.isBackgroundTimeoutTriggered(context))
    }

    @Test
    fun `worker uses default timeout when not provided in input data`() = runBlocking {
        // Set up an old interaction timestamp (older than default 15 minutes)
        val defaultTimeoutMinutes = 15L
        val defaultTimeoutMillis = defaultTimeoutMinutes * 60 * 1000L
        val lastInteractionTime = System.currentTimeMillis() - defaultTimeoutMillis - 1000L
        IdlePersistence.recordInteraction(context, lastInteractionTime)

        // Create worker without timeout configuration (should use default)
        val worker = TestListenableWorkerBuilder<BackgroundTimeoutWorker>(context).build()

        // Execute worker
        val result = worker.doWork()

        // Verify worker completed successfully
        assertEquals(ListenableWorker.Result.success(), result)

        // Verify timeout flag was set (using default timeout)
        assertTrue(IdlePersistence.isBackgroundTimeoutTriggered(context))
    }

    @Test
    fun `worker handles no previous interaction timestamp gracefully`() = runBlocking {
        // Don't set any previous interaction (timestamp will be 0)
        val timeoutMillis = 5000L

        // Create worker with timeout configuration
        val inputData = workDataOf(BackgroundTimeoutWorker.KEY_TIMEOUT_MILLIS to timeoutMillis)
        val worker = TestListenableWorkerBuilder<BackgroundTimeoutWorker>(context)
            .setInputData(inputData)
            .build()

        // Execute worker
        val result = worker.doWork()

        // Verify worker completed successfully
        assertEquals(ListenableWorker.Result.success(), result)

        // Verify timeout flag was not set (no valid timestamp)
        assertFalse(IdlePersistence.isBackgroundTimeoutTriggered(context))
    }

    @Test
    fun `worker handles edge case where interaction time equals timeout threshold`() = runBlocking {
        // Set up interaction timestamp exactly at the timeout threshold
        val timeoutMillis = 5000L
        val lastInteractionTime = System.currentTimeMillis() - timeoutMillis
        IdlePersistence.recordInteraction(context, lastInteractionTime)

        // Create worker with timeout configuration
        val inputData = workDataOf(BackgroundTimeoutWorker.KEY_TIMEOUT_MILLIS to timeoutMillis)
        val worker = TestListenableWorkerBuilder<BackgroundTimeoutWorker>(context)
            .setInputData(inputData)
            .build()

        // Execute worker
        val result = worker.doWork()

        // Verify worker completed successfully
        assertEquals(ListenableWorker.Result.success(), result)

        // Verify timeout flag was set (>= condition should trigger)
        assertTrue(IdlePersistence.isBackgroundTimeoutTriggered(context))
    }

    @Test
    fun `worker resets timeout flag when condition is not met`() = runBlocking {
        // First set the timeout flag to true
        IdlePersistence.setBackgroundTimeoutTriggered(context, true)
        assertTrue(IdlePersistence.isBackgroundTimeoutTriggered(context))

        // Set up a recent interaction timestamp (within threshold)
        val timeoutMillis = 5000L
        val lastInteractionTime = System.currentTimeMillis() - 1000L // 1 second ago
        IdlePersistence.recordInteraction(context, lastInteractionTime)

        // Create worker with timeout configuration
        val inputData = workDataOf(BackgroundTimeoutWorker.KEY_TIMEOUT_MILLIS to timeoutMillis)
        val worker = TestListenableWorkerBuilder<BackgroundTimeoutWorker>(context)
            .setInputData(inputData)
            .build()

        // Execute worker
        val result = worker.doWork()

        // Verify worker completed successfully
        assertEquals(ListenableWorker.Result.success(), result)

        // Verify timeout flag was reset to false
        assertFalse(IdlePersistence.isBackgroundTimeoutTriggered(context))
    }

    @Test
    fun `worker handles very large timeout values`() = runBlocking {
        // Test with a very large timeout value
        val timeoutMillis = Long.MAX_VALUE
        val lastInteractionTime = System.currentTimeMillis() - 1000L
        IdlePersistence.recordInteraction(context, lastInteractionTime)

        // Create worker with large timeout configuration
        val inputData = workDataOf(BackgroundTimeoutWorker.KEY_TIMEOUT_MILLIS to timeoutMillis)
        val worker = TestListenableWorkerBuilder<BackgroundTimeoutWorker>(context)
            .setInputData(inputData)
            .build()

        // Execute worker
        val result = worker.doWork()

        // Verify worker completed successfully without throwing
        assertEquals(ListenableWorker.Result.success(), result)

        // With such a large timeout, condition should not be met
        assertFalse(IdlePersistence.isBackgroundTimeoutTriggered(context))
    }
}
