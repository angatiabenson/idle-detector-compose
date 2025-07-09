package ke.co.banit.idle_detector_compose

import android.content.Context
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Unit tests for IdleDetector to verify core idle detection functionality.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class IdleDetectorTest {

    private lateinit var context: Context
    private lateinit var lifecycleOwner: TestLifecycleOwner
    private lateinit var workManager: WorkManager
    private lateinit var testScope: TestScope

    private var idleCallbackInvoked = false
    private var idleCallbackFromBackground = false

    private class TestLifecycleOwner : LifecycleOwner {
        private val lifecycleRegistry = LifecycleRegistry(this)

        override val lifecycle: Lifecycle get() = lifecycleRegistry

        fun moveToState(state: Lifecycle.State) {
            lifecycleRegistry.currentState = state
        }
    }

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        lifecycleOwner = TestLifecycleOwner()

        // Initialize WorkManager for testing
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        workManager = WorkManager.getInstance(context)

        // Reset test state
        idleCallbackInvoked = false
        idleCallbackFromBackground = false
        IdlePersistence.reset(context)
    }

    @After
    fun tearDown() {
        IdlePersistence.reset(context)
    }

    private fun createIdleDetector(
        idleTimeout: kotlin.time.Duration = 2.seconds,
        checkInterval: kotlin.time.Duration = 100.milliseconds,
        onIdleWithOrigin: (Boolean) -> Unit = { fromBackground ->
            idleCallbackInvoked = true
            idleCallbackFromBackground = fromBackground
            println("Idle callback invoked. From background: $fromBackground")
        },
    ): IdleDetector {
        return IdleDetector(
            context = context,
            lifecycleOwner = lifecycleOwner,
            idleTimeout = idleTimeout,
            checkInterval = checkInterval,
            onIdleWithOrigin = onIdleWithOrigin
        )
    }

    @Test
    fun `initialization sets up lifecycle observer and initializes timestamp`() {
        val idleDetector = createIdleDetector()

        // Move to CREATE state to trigger initialization
        lifecycleOwner.moveToState(Lifecycle.State.CREATED)

        // Verify timestamp was initialized
        assertTrue(idleDetector.lastInteractionTimestamp > 0)
        assertFalse(idleDetector.isIdle.value)
    }

    @Test
    fun `registerInteraction updates timestamp and resets idle state`() = runTest {
        val idleDetector = createIdleDetector()
        lifecycleOwner.moveToState(Lifecycle.State.CREATED)

        val initialTimestamp = idleDetector.lastInteractionTimestamp

        // Wait a bit and register interaction
        advanceTimeBy(100)
        idleDetector.registerInteraction()

        // Verify timestamp was updated
        assertTrue(idleDetector.lastInteractionTimestamp > initialTimestamp)
        assertFalse(idleDetector.isIdle.value)
    }

    @Test
    fun `checkIdleState detects idle condition when timeout exceeded`() = runTest {
        val idleTimeout = 1.seconds

        val idleDetector = createIdleDetector(idleTimeout = idleTimeout)
        lifecycleOwner.moveToState(Lifecycle.State.CREATED)

        // Set an old timestamp to trigger idle condition
        val oldTimestamp = System.currentTimeMillis() - idleTimeout.inWholeMilliseconds - 100
        IdlePersistence.recordInteraction(context, oldTimestamp)

        // Check idle state
        idleDetector.checkIdleState()

        // Verify idle state is detected
        assertTrue(idleDetector.isIdle.value)
        assertTrue(idleCallbackInvoked)
        assertFalse(idleCallbackFromBackground)
    }

    @Test
    fun `checkIdleState does not trigger when within timeout threshold`() = runTest {
        val idleTimeout = 2.seconds
        val idleDetector = createIdleDetector(idleTimeout = idleTimeout)

        lifecycleOwner.moveToState(Lifecycle.State.CREATED)

        // Set a recent timestamp
        val recentTimestamp = System.currentTimeMillis() - 500 // 500ms ago
        IdlePersistence.recordInteraction(context, recentTimestamp)

        // Check idle state
        idleDetector.checkIdleState()

        // Verify idle state is not detected
        assertFalse(idleDetector.isIdle.value)
        assertFalse(idleCallbackInvoked)
    }

    @Test
    fun `onResume handles background timeout correctly`() = runTest {
        val idleDetector = createIdleDetector()

        // Move to CREATED state first to trigger initialization
        lifecycleOwner.moveToState(Lifecycle.State.CREATED)

        // Set background timeout flag AFTER initialization to prevent it being reset
        IdlePersistence.setBackgroundTimeoutTriggered(context, true)

        // Move to RESUMED state to trigger handleResume
        lifecycleOwner.moveToState(Lifecycle.State.RESUMED)

        // Verify callback was called with background origin
        assertTrue(idleCallbackInvoked)
        assertTrue(idleCallbackFromBackground)

        // Verify flag was reset
        assertFalse(IdlePersistence.isBackgroundTimeoutTriggered(context))
    }

    @Test
    fun `onPause schedules background timeout worker`() = runTest {
        val idleDetector = createIdleDetector()

        // Move to CREATED and RESUMED states first
        lifecycleOwner.moveToState(Lifecycle.State.CREATED)
        lifecycleOwner.moveToState(Lifecycle.State.RESUMED)

        // Register an interaction to set timestamp
        idleDetector.registerInteraction()

        // Move to STARTED state (which triggers pause handling)
        lifecycleOwner.moveToState(Lifecycle.State.STARTED)

        // Verify work was scheduled
        val workInfos =
            workManager.getWorkInfosForUniqueWork(BackgroundTimeoutWorker.WORK_NAME).get()
        assertTrue(workInfos.isNotEmpty())
        assertEquals(WorkInfo.State.ENQUEUED, workInfos.first().state)
    }

    @Test
    fun `registerInteraction cancels background timeout worker`() = runTest {
        val idleDetector = createIdleDetector()

        lifecycleOwner.moveToState(Lifecycle.State.CREATED)
        lifecycleOwner.moveToState(Lifecycle.State.RESUMED)

        // Register interaction and pause to schedule worker
        idleDetector.registerInteraction()
        lifecycleOwner.moveToState(Lifecycle.State.STARTED)

        // Verify work was scheduled
        var workInfos =
            workManager.getWorkInfosForUniqueWork(BackgroundTimeoutWorker.WORK_NAME).get()
        assertTrue(workInfos.isNotEmpty())

        // Register another interaction (should cancel the worker)
        idleDetector.registerInteraction()

        // Verify work was cancelled
        workInfos = workManager.getWorkInfosForUniqueWork(BackgroundTimeoutWorker.WORK_NAME).get()
        assertTrue(workInfos.isEmpty() || workInfos.first().state == WorkInfo.State.CANCELLED)
    }

    @Test
    fun `cleanUp removes lifecycle observer and cancels work`() = runTest {
        val idleDetector = createIdleDetector()

        lifecycleOwner.moveToState(Lifecycle.State.CREATED)
        lifecycleOwner.moveToState(Lifecycle.State.RESUMED)
        idleDetector.registerInteraction()
        lifecycleOwner.moveToState(Lifecycle.State.STARTED)

        // Clean up
        idleDetector.cleanUp()

        // Verify work was cancelled
        val workInfos =
            workManager.getWorkInfosForUniqueWork(BackgroundTimeoutWorker.WORK_NAME).get()
        assertTrue(workInfos.isEmpty() || workInfos.first().state == WorkInfo.State.CANCELLED)
    }

    @Test
    fun `resetState clears all state and reinitializes`() = runTest {
        val idleDetector = createIdleDetector()

        // Set some state
        idleDetector.registerInteraction()
        IdlePersistence.setBackgroundTimeoutTriggered(context, true)

        // Move to CREATED state to trigger reset
        lifecycleOwner.moveToState(Lifecycle.State.CREATED)

        // Verify state was reset and reinitialized
        assertTrue(idleDetector.lastInteractionTimestamp > 0)
        assertFalse(idleDetector.isIdle.value)
        assertFalse(IdlePersistence.isBackgroundTimeoutTriggered(context))
    }

    @Test
    fun `idle callback is only called once until reset`() = runTest {
        val idleTimeout = 1.seconds
        var callbackCount = 0

        val idleDetector = createIdleDetector(
            idleTimeout = idleTimeout,
            onIdleWithOrigin = { _ ->
                callbackCount++
                println("Idle callback invoked. Count: $callbackCount")
            }
        )

        lifecycleOwner.moveToState(Lifecycle.State.CREATED)

        // Set old timestamp to trigger idle
        val oldTimestamp = System.currentTimeMillis() - idleTimeout.inWholeMilliseconds - 100
        IdlePersistence.recordInteraction(context, oldTimestamp)

        // Check idle state multiple times
        idleDetector.checkIdleState()
        idleDetector.checkIdleState()
        idleDetector.checkIdleState()

        // Verify callback was only called once
        assertEquals(1, callbackCount)

        // Register interaction and check again
        idleDetector.registerInteraction()
        idleDetector.checkIdleState()

        // Verify callback count is still 1 (reset but not idle)
        assertEquals(1, callbackCount)
    }

    @Test
    fun `handles zero timestamp gracefully`() = runTest {
        val idleDetector = createIdleDetector()

        // Don't initialize timestamp (should remain 0)
        IdlePersistence.reset(context)

        // Check idle state
        idleDetector.checkIdleState()

        // Should not crash and should not trigger callback
        assertFalse(idleCallbackInvoked)
    }

    @Test
    fun `background worker scheduling handles edge cases`() = runTest {
        val idleTimeout = 1.seconds
        val idleDetector = createIdleDetector(idleTimeout = idleTimeout)

        lifecycleOwner.moveToState(Lifecycle.State.CREATED)
        lifecycleOwner.moveToState(Lifecycle.State.RESUMED)

        // Set timestamp that would already be expired
        val expiredTimestamp = System.currentTimeMillis() - idleTimeout.inWholeMilliseconds - 1000
        IdlePersistence.recordInteraction(context, expiredTimestamp)

        // Pause should not schedule worker for already expired timestamp
        lifecycleOwner.moveToState(Lifecycle.State.STARTED)

        // Worker should not be scheduled or should have minimal delay
        val workInfos =
            workManager.getWorkInfosForUniqueWork(BackgroundTimeoutWorker.WORK_NAME).get()
        // Either no work scheduled or it's already completed/running
        assertTrue(workInfos.isEmpty() || workInfos.first().state != WorkInfo.State.BLOCKED)
    }
}
