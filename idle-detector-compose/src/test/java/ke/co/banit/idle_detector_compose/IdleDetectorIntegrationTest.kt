package ke.co.banit.idle_detector_compose

import android.content.Context
import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Integration tests to verify the complete idle detection workflow.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class IdleDetectorIntegrationTest {

    private lateinit var context: Context
    private lateinit var lifecycleOwner: TestLifecycleOwner
    private lateinit var workManager: WorkManager

    private var idleCallbackCount = 0
    private var lastIdleOrigin: Boolean? = null

    private class TestLifecycleOwner : LifecycleOwner {
        private val lifecycleRegistry = LifecycleRegistry(this)

        override val lifecycle: Lifecycle get() = lifecycleRegistry

        fun moveToState(state: Lifecycle.State) {
            // Use handleLifecycleEvent to properly trigger observers
            when (state) {
                Lifecycle.State.INITIALIZED -> {
                    if (lifecycleRegistry.currentState >= Lifecycle.State.RESUMED) {
                        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
                    }
                    if (lifecycleRegistry.currentState >= Lifecycle.State.STARTED) {
                        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
                    }
                    if (lifecycleRegistry.currentState >= Lifecycle.State.CREATED) {
                        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
                    }
                }
                Lifecycle.State.CREATED -> {
                    if (lifecycleRegistry.currentState >= Lifecycle.State.RESUMED) {
                        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
                    }
                    if (lifecycleRegistry.currentState >= Lifecycle.State.STARTED) {
                        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
                    }
                    if (lifecycleRegistry.currentState < Lifecycle.State.CREATED) {
                        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
                    }
                }
                Lifecycle.State.STARTED -> {
                    if (lifecycleRegistry.currentState < Lifecycle.State.CREATED) {
                        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
                    }
                    if (lifecycleRegistry.currentState >= Lifecycle.State.RESUMED) {
                        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
                    }
                    if (lifecycleRegistry.currentState < Lifecycle.State.STARTED) {
                        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
                    }
                }
                Lifecycle.State.RESUMED -> {
                    if (lifecycleRegistry.currentState < Lifecycle.State.CREATED) {
                        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
                    }
                    if (lifecycleRegistry.currentState < Lifecycle.State.STARTED) {
                        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
                    }
                    if (lifecycleRegistry.currentState < Lifecycle.State.RESUMED) {
                        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
                    }
                }
                Lifecycle.State.DESTROYED -> {
                    if (lifecycleRegistry.currentState >= Lifecycle.State.RESUMED) {
                        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
                    }
                    if (lifecycleRegistry.currentState >= Lifecycle.State.STARTED) {
                        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
                    }
                    if (lifecycleRegistry.currentState >= Lifecycle.State.CREATED) {
                        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
                    }
                }
            }
        }
    }

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        lifecycleOwner = TestLifecycleOwner()

        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        workManager = WorkManager.getInstance(context)

        idleCallbackCount = 0
        lastIdleOrigin = null
        IdlePersistence.reset(context)
    }

    @After
    fun tearDown() {
        IdlePersistence.reset(context)
    }

    private fun createIdleDetector(
        idleTimeout: kotlin.time.Duration = 2.seconds,
    ): IdleDetector {
        return IdleDetector(
            context = context,
            lifecycleOwner = lifecycleOwner,
            idleTimeout = idleTimeout,
            checkInterval = 100.milliseconds,
            onIdleWithOrigin = { fromBackground ->
                idleCallbackCount++
                lastIdleOrigin = fromBackground
            }
        )
    }

    @Test
    fun `complete foreground idle detection workflow`() = runTest {
        val idleTimeout = 1.seconds
        val idleDetector = createIdleDetector(idleTimeout)

        // 1. Initialize detector
        lifecycleOwner.moveToState(Lifecycle.State.CREATED)
        shadowOf(Looper.getMainLooper()).idle()
        lifecycleOwner.moveToState(Lifecycle.State.RESUMED)
        shadowOf(Looper.getMainLooper()).idle()

        // 2. Verify initial state
        assertTrue(idleDetector.lastInteractionTimestamp > 0)
        assertFalse(idleDetector.isIdle.value)
        assertEquals(0, idleCallbackCount)

        // 3. Set old timestamp to simulate idle condition - need to update the detector's internal state
        val oldTimestamp = System.currentTimeMillis() - idleTimeout.inWholeMilliseconds - 100
        IdlePersistence.recordInteraction(context, oldTimestamp)

        // Reset the detector to pick up the new timestamp from persistence
        lifecycleOwner.moveToState(Lifecycle.State.CREATED)
        shadowOf(Looper.getMainLooper()).idle()
        lifecycleOwner.moveToState(Lifecycle.State.RESUMED)
        shadowOf(Looper.getMainLooper()).idle()

        // 4. Check idle state - now the detector should have the old timestamp
        idleDetector.checkIdleState()

        // 5. Verify idle detection
        assertTrue(idleDetector.isIdle.value)
        assertEquals(1, idleCallbackCount)
        assertEquals(false, lastIdleOrigin) // Foreground detection

        // 6. Register new interaction
        idleDetector.registerInteraction()

        // 7. Verify state reset
        assertFalse(idleDetector.isIdle.value)
        assertTrue(idleDetector.lastInteractionTimestamp > oldTimestamp)

        idleDetector.cleanUp()
    }

    @Test
    fun `complete background idle detection workflow`() = runTest {
        val idleTimeout = 1.seconds
        val idleDetector = createIdleDetector(idleTimeout)

        // 1. Initialize and activate detector
        lifecycleOwner.moveToState(Lifecycle.State.CREATED)
        shadowOf(Looper.getMainLooper()).idle()
        lifecycleOwner.moveToState(Lifecycle.State.RESUMED)
        shadowOf(Looper.getMainLooper()).idle()

        // 2. Register interaction and pause (simulate app going to background)
        idleDetector.registerInteraction()
        lifecycleOwner.moveToState(Lifecycle.State.STARTED) // Triggers pause
        shadowOf(Looper.getMainLooper()).idle()

        // 3. Verify background worker was scheduled
        val workInfos =
            workManager.getWorkInfosForUniqueWork(BackgroundTimeoutWorker.WORK_NAME).get()
        assertTrue(workInfos.isNotEmpty())
        assertEquals(WorkInfo.State.ENQUEUED, workInfos.first().state)

        // 4. Simulate background timeout by setting the flag
        val oldTimestamp = System.currentTimeMillis() - idleTimeout.inWholeMilliseconds - 100
        IdlePersistence.recordInteraction(context, oldTimestamp)
        IdlePersistence.setBackgroundTimeoutTriggered(context, true)

        // 5. Resume app
        lifecycleOwner.moveToState(Lifecycle.State.RESUMED)
        shadowOf(Looper.getMainLooper()).idle()

        // 6. Verify background idle was detected
        assertEquals(1, idleCallbackCount)
        assertEquals(true, lastIdleOrigin) // Background detection
        assertFalse(IdlePersistence.isBackgroundTimeoutTriggered(context)) // Flag reset

        idleDetector.cleanUp()
    }

    @Test
    fun `foreground and background idle detection coordination`() = runTest {
        val idleTimeout = 1.seconds
        val idleDetector = createIdleDetector(idleTimeout)

        // 1. Initialize
        lifecycleOwner.moveToState(Lifecycle.State.CREATED)
        shadowOf(Looper.getMainLooper()).idle()
        lifecycleOwner.moveToState(Lifecycle.State.RESUMED)
        shadowOf(Looper.getMainLooper()).idle()

        // Reset detector to pick up the old timestamp
        lifecycleOwner.moveToState(Lifecycle.State.CREATED)
        shadowOf(Looper.getMainLooper()).idle()

        // 2. Trigger foreground idle first - need to properly simulate this
        val oldTimestamp = System.currentTimeMillis() - idleTimeout.inWholeMilliseconds - 100
        IdlePersistence.recordInteraction(context, oldTimestamp)

        lifecycleOwner.moveToState(Lifecycle.State.RESUMED)
        shadowOf(Looper.getMainLooper()).idle()
        idleDetector.checkIdleState()

        assertEquals(1, idleCallbackCount)
        assertEquals(false, lastIdleOrigin)

        // 3. Register interaction and pause
        lifecycleOwner.moveToState(Lifecycle.State.STARTED)
        shadowOf(Looper.getMainLooper()).idle()

        // 4. Simulate background timeout
        IdlePersistence.setBackgroundTimeoutTriggered(context, true)

        // 5. Resume - should not trigger callback again since foreground already did
        lifecycleOwner.moveToState(Lifecycle.State.RESUMED)
        shadowOf(Looper.getMainLooper()).idle()

        // 6. Callback count should remain 1 (not called again for background)
        // Background timeout should be cleared but callback not called again due to _onIdleCalled flag
        assertEquals(1, idleCallbackCount)
        assertFalse(IdlePersistence.isBackgroundTimeoutTriggered(context)) // Flag should be reset

        idleDetector.cleanUp()
    }

    @Test
    fun `multiple lifecycle transitions preserve state correctly`() = runTest {
        val idleDetector = createIdleDetector()

        // 1. Initial lifecycle
        lifecycleOwner.moveToState(Lifecycle.State.CREATED)
        shadowOf(Looper.getMainLooper()).idle()
        val initialTimestamp = IdlePersistence.getLastInteractionTimestamp(context)
        assertTrue(initialTimestamp > 0)

        // 2. Start and resume
        lifecycleOwner.moveToState(Lifecycle.State.STARTED)
        shadowOf(Looper.getMainLooper()).idle()
        lifecycleOwner.moveToState(Lifecycle.State.RESUMED)
        shadowOf(Looper.getMainLooper()).idle()
        idleDetector.registerInteraction()
        val activeTimestamp = IdlePersistence.getLastInteractionTimestamp(context)
        assertTrue(activeTimestamp >= initialTimestamp)

        // 3. Pause and background work
        lifecycleOwner.moveToState(Lifecycle.State.STARTED)
        shadowOf(Looper.getMainLooper()).idle()
        var workInfos =
            workManager.getWorkInfosForUniqueWork(BackgroundTimeoutWorker.WORK_NAME).get()
        assertTrue(workInfos.isNotEmpty())

        // 4. Resume cancels background work
        lifecycleOwner.moveToState(Lifecycle.State.RESUMED)
        shadowOf(Looper.getMainLooper()).idle()
        advanceTimeBy(100) // Allow time for cancellation
        workInfos = workManager.getWorkInfosForUniqueWork(BackgroundTimeoutWorker.WORK_NAME).get()
        assertTrue(workInfos.isEmpty() || workInfos.first().state == WorkInfo.State.CANCELLED)

        // 5. State should be preserved
        assertEquals(activeTimestamp, IdlePersistence.getLastInteractionTimestamp(context))

        idleDetector.cleanUp()
    }

    @Test
    fun `reset functionality clears all state`() = runTest {
        val idleDetector = createIdleDetector()

        // 1. Set up state
        lifecycleOwner.moveToState(Lifecycle.State.CREATED)
        shadowOf(Looper.getMainLooper()).idle()
        lifecycleOwner.moveToState(Lifecycle.State.RESUMED)
        shadowOf(Looper.getMainLooper()).idle()
        idleDetector.registerInteraction()
        IdlePersistence.setBackgroundTimeoutTriggered(context, true)

        // 2. Verify state exists
        assertTrue(IdlePersistence.getLastInteractionTimestamp(context) > 0)
        assertTrue(IdlePersistence.isBackgroundTimeoutTriggered(context))

        // 3. Reset through lifecycle (CREATE triggers reset)
        lifecycleOwner.moveToState(Lifecycle.State.DESTROYED)
        shadowOf(Looper.getMainLooper()).idle()

        // 4. Verify state was reset and reinitialized
        assertFalse(IdlePersistence.isBackgroundTimeoutTriggered(context))
        assertFalse(IdlePersistence.getLastInteractionTimestamp(context) > 0) // Reinitialized

        idleDetector.cleanUp()
    }
}
