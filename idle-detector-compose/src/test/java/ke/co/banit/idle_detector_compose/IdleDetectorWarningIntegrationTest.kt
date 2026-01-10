package ke.co.banit.idle_detector_compose

import android.content.Context
import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
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
 * Integration tests for warning timeout and countdown timer functionality.
 *
 * Tests the complete warning detection workflow, timer state updates,
 * and interaction between warning and idle states.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class IdleDetectorWarningIntegrationTest {

    private lateinit var context: Context
    private lateinit var lifecycleOwner: TestLifecycleOwner
    private lateinit var workManager: WorkManager

    private var idleCallbackCount = 0
    private var warningCallbackCount = 0
    private var lastIdleOrigin: Boolean? = null
    private var lastWarningRemainingTime: Duration? = null

    private class TestLifecycleOwner : LifecycleOwner {
        private val lifecycleRegistry = LifecycleRegistry(this)

        override val lifecycle: Lifecycle get() = lifecycleRegistry

        fun moveToState(state: Lifecycle.State) {
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
        warningCallbackCount = 0
        lastIdleOrigin = null
        lastWarningRemainingTime = null
        IdlePersistence.reset(context)
    }

    @After
    fun tearDown() {
        IdlePersistence.reset(context)
    }

    private fun createIdleDetector(
        idleTimeout: Duration = 2.seconds,
        warningTimeout: Duration? = null,
    ): IdleDetector {
        return IdleDetector(
            context = context,
            lifecycleOwner = lifecycleOwner,
            idleTimeout = idleTimeout,
            warningTimeout = warningTimeout,
            checkInterval = 100.milliseconds,
            onIdleWithOrigin = { fromBackground ->
                idleCallbackCount++
                lastIdleOrigin = fromBackground
            },
            onWarning = warningTimeout?.let {
                { remaining ->
                    warningCallbackCount++
                    lastWarningRemainingTime = remaining
                }
            }
        )
    }

    // region Warning Callback Tests

    @Test
    fun `warning callback fires when entering warning period`() = runTest {
        val idleTimeout = 2.seconds
        val warningTimeout = 500.milliseconds
        val idleDetector = createIdleDetector(idleTimeout, warningTimeout)

        // Initialize detector
        lifecycleOwner.moveToState(Lifecycle.State.CREATED)
        shadowOf(Looper.getMainLooper()).idle()
        lifecycleOwner.moveToState(Lifecycle.State.RESUMED)
        shadowOf(Looper.getMainLooper()).idle()

        // Set timestamp to just enter warning period (1.6 seconds elapsed, 400ms remaining)
        val warningTimestamp = System.currentTimeMillis() - 1600
        IdlePersistence.recordInteraction(context, warningTimestamp)

        // Trigger check
        idleDetector.checkIdleState()

        // Verify warning was triggered
        assertEquals(1, warningCallbackCount)
        assertNotNull(lastWarningRemainingTime)
        assertTrue(lastWarningRemainingTime!! <= warningTimeout)
        assertEquals(0, idleCallbackCount) // Not idle yet

        idleDetector.cleanUp()
    }

    @Test
    fun `warning callback fires only once per warning period`() = runTest {
        val idleTimeout = 2.seconds
        val warningTimeout = 500.milliseconds
        val idleDetector = createIdleDetector(idleTimeout, warningTimeout)

        // Initialize
        lifecycleOwner.moveToState(Lifecycle.State.CREATED)
        shadowOf(Looper.getMainLooper()).idle()
        lifecycleOwner.moveToState(Lifecycle.State.RESUMED)
        shadowOf(Looper.getMainLooper()).idle()

        // Set timestamp to enter warning period
        val warningTimestamp = System.currentTimeMillis() - 1600
        IdlePersistence.recordInteraction(context, warningTimestamp)

        // Check multiple times
        idleDetector.checkIdleState()
        idleDetector.checkIdleState()
        idleDetector.checkIdleState()

        // Warning should only fire once
        assertEquals(1, warningCallbackCount)

        idleDetector.cleanUp()
    }

    @Test
    fun `warning callback resets after user interaction`() = runTest {
        val idleTimeout = 2.seconds
        val warningTimeout = 500.milliseconds
        val idleDetector = createIdleDetector(idleTimeout, warningTimeout)

        // Initialize
        lifecycleOwner.moveToState(Lifecycle.State.CREATED)
        shadowOf(Looper.getMainLooper()).idle()
        lifecycleOwner.moveToState(Lifecycle.State.RESUMED)
        shadowOf(Looper.getMainLooper()).idle()

        // Enter warning period
        val warningTimestamp = System.currentTimeMillis() - 1600
        IdlePersistence.recordInteraction(context, warningTimestamp)
        idleDetector.checkIdleState()
        assertEquals(1, warningCallbackCount)

        // Register new interaction (user "woke up")
        idleDetector.registerInteraction()

        // Enter warning period again
        val newWarningTimestamp = System.currentTimeMillis() - 1600
        IdlePersistence.recordInteraction(context, newWarningTimestamp)
        idleDetector.checkIdleState()

        // Warning should fire again
        assertEquals(2, warningCallbackCount)

        idleDetector.cleanUp()
    }

    @Test
    fun `no warning callback when warning timeout not configured`() = runTest {
        val idleTimeout = 2.seconds
        val idleDetector = createIdleDetector(idleTimeout, warningTimeout = null)

        // Initialize
        lifecycleOwner.moveToState(Lifecycle.State.CREATED)
        shadowOf(Looper.getMainLooper()).idle()
        lifecycleOwner.moveToState(Lifecycle.State.RESUMED)
        shadowOf(Looper.getMainLooper()).idle()

        // Set timestamp to what would be warning period if configured
        val timestamp = System.currentTimeMillis() - 1600
        IdlePersistence.recordInteraction(context, timestamp)

        idleDetector.checkIdleState()

        // No warning callback should be made
        assertEquals(0, warningCallbackCount)
        assertNull(lastWarningRemainingTime)

        idleDetector.cleanUp()
    }

    @Test
    fun `warning fires before idle in correct sequence`() = runTest {
        val idleTimeout = 1.seconds
        val warningTimeout = 300.milliseconds
        val idleDetector = createIdleDetector(idleTimeout, warningTimeout)

        // Initialize
        lifecycleOwner.moveToState(Lifecycle.State.CREATED)
        shadowOf(Looper.getMainLooper()).idle()
        lifecycleOwner.moveToState(Lifecycle.State.RESUMED)
        shadowOf(Looper.getMainLooper()).idle()

        // Set timestamp to enter warning period (800ms elapsed)
        val warningTimestamp = System.currentTimeMillis() - 800
        IdlePersistence.recordInteraction(context, warningTimestamp)
        idleDetector.checkIdleState()

        assertEquals(1, warningCallbackCount)
        assertEquals(0, idleCallbackCount)

        // Now set timestamp to idle state (1100ms elapsed)
        val idleTimestamp = System.currentTimeMillis() - 1100
        IdlePersistence.recordInteraction(context, idleTimestamp)
        idleDetector.checkIdleState()

        assertEquals(1, warningCallbackCount) // Still 1 (already called)
        assertEquals(1, idleCallbackCount)    // Now idle

        idleDetector.cleanUp()
    }

    // endregion

    // region Timer State Tests

    @Test
    fun `timer state updates with remaining time`() = runTest {
        val idleTimeout = 5.seconds
        val idleDetector = createIdleDetector(idleTimeout)

        // Initialize
        lifecycleOwner.moveToState(Lifecycle.State.CREATED)
        shadowOf(Looper.getMainLooper()).idle()
        lifecycleOwner.moveToState(Lifecycle.State.RESUMED)
        shadowOf(Looper.getMainLooper()).idle()

        // Check initial state
        val initialState = idleDetector.timerState.value
        assertEquals(idleTimeout, initialState.totalTimeout)
        assertFalse(initialState.isIdle)

        // Set elapsed time
        val timestamp = System.currentTimeMillis() - 2000  // 2 seconds elapsed
        IdlePersistence.recordInteraction(context, timestamp)
        idleDetector.checkIdleState()

        val updatedState = idleDetector.timerState.value
        assertTrue(updatedState.remainingTime <= 3.seconds)
        assertTrue(updatedState.remainingTime >= 2.seconds)
        assertFalse(updatedState.isIdle)

        idleDetector.cleanUp()
    }

    @Test
    fun `timer state shows warning period correctly`() = runTest {
        val idleTimeout = 2.seconds
        val warningTimeout = 500.milliseconds
        val idleDetector = createIdleDetector(idleTimeout, warningTimeout)

        // Initialize
        lifecycleOwner.moveToState(Lifecycle.State.CREATED)
        shadowOf(Looper.getMainLooper()).idle()
        lifecycleOwner.moveToState(Lifecycle.State.RESUMED)
        shadowOf(Looper.getMainLooper()).idle()

        // Before warning period
        val beforeWarningTimestamp = System.currentTimeMillis() - 1000  // 1 second elapsed
        IdlePersistence.recordInteraction(context, beforeWarningTimestamp)
        idleDetector.checkIdleState()

        assertFalse(idleDetector.timerState.value.isInWarningPeriod)

        // In warning period
        val inWarningTimestamp = System.currentTimeMillis() - 1700  // 1.7 seconds elapsed
        IdlePersistence.recordInteraction(context, inWarningTimestamp)
        idleDetector.checkIdleState()

        assertTrue(idleDetector.timerState.value.isInWarningPeriod)
        assertEquals(warningTimeout, idleDetector.timerState.value.warningThreshold)

        idleDetector.cleanUp()
    }

    @Test
    fun `timer state progress increases over time`() = runTest {
        val idleTimeout = 4.seconds
        val idleDetector = createIdleDetector(idleTimeout)

        // Initialize
        lifecycleOwner.moveToState(Lifecycle.State.CREATED)
        shadowOf(Looper.getMainLooper()).idle()
        lifecycleOwner.moveToState(Lifecycle.State.RESUMED)
        shadowOf(Looper.getMainLooper()).idle()

        // At start (0% progress)
        idleDetector.registerInteraction()
        idleDetector.checkIdleState()
        val startProgress = idleDetector.timerState.value.progress
        assertTrue(startProgress < 0.1f)

        // At 25% (1 second elapsed)
        val quarterTimestamp = System.currentTimeMillis() - 1000
        IdlePersistence.recordInteraction(context, quarterTimestamp)
        idleDetector.checkIdleState()
        val quarterProgress = idleDetector.timerState.value.progress
        assertTrue(quarterProgress >= 0.2f && quarterProgress <= 0.35f)

        // At 50% (2 seconds elapsed)
        val halfTimestamp = System.currentTimeMillis() - 2000
        IdlePersistence.recordInteraction(context, halfTimestamp)
        idleDetector.checkIdleState()
        val halfProgress = idleDetector.timerState.value.progress
        assertTrue(halfProgress >= 0.45f && halfProgress <= 0.6f)

        idleDetector.cleanUp()
    }

    @Test
    fun `timer state resets on interaction`() = runTest {
        val idleTimeout = 2.seconds
        val idleDetector = createIdleDetector(idleTimeout)

        // Initialize
        lifecycleOwner.moveToState(Lifecycle.State.CREATED)
        shadowOf(Looper.getMainLooper()).idle()
        lifecycleOwner.moveToState(Lifecycle.State.RESUMED)
        shadowOf(Looper.getMainLooper()).idle()

        // Advance time
        val oldTimestamp = System.currentTimeMillis() - 1500
        IdlePersistence.recordInteraction(context, oldTimestamp)
        idleDetector.checkIdleState()

        val progressBeforeInteraction = idleDetector.timerState.value.progress
        assertTrue(progressBeforeInteraction > 0.5f)

        // Register interaction
        idleDetector.registerInteraction()

        // Progress should be near zero
        val progressAfterInteraction = idleDetector.timerState.value.progress
        assertTrue(progressAfterInteraction < 0.1f)

        idleDetector.cleanUp()
    }

    @Test
    fun `timer state formatted time is correct`() = runTest {
        val idleTimeout = 5.seconds
        val idleDetector = createIdleDetector(idleTimeout)

        // Initialize
        lifecycleOwner.moveToState(Lifecycle.State.CREATED)
        shadowOf(Looper.getMainLooper()).idle()
        lifecycleOwner.moveToState(Lifecycle.State.RESUMED)
        shadowOf(Looper.getMainLooper()).idle()

        // At start
        idleDetector.registerInteraction()
        val startFormatted = idleDetector.timerState.value.formattedRemainingTime
        assertEquals("00:05", startFormatted)

        // After 2 seconds
        val timestamp = System.currentTimeMillis() - 2000
        IdlePersistence.recordInteraction(context, timestamp)
        idleDetector.checkIdleState()
        val afterFormatted = idleDetector.timerState.value.formattedRemainingTime
        assertTrue(afterFormatted == "00:03" || afterFormatted == "00:02")

        idleDetector.cleanUp()
    }

    // endregion

    // region Warning + Idle Integration Tests

    @Test
    fun `complete warning to idle flow`() = runTest {
        val idleTimeout = 2.seconds
        val warningTimeout = 500.milliseconds
        val idleDetector = createIdleDetector(idleTimeout, warningTimeout)

        // Initialize
        lifecycleOwner.moveToState(Lifecycle.State.CREATED)
        shadowOf(Looper.getMainLooper()).idle()
        lifecycleOwner.moveToState(Lifecycle.State.RESUMED)
        shadowOf(Looper.getMainLooper()).idle()

        // Phase 1: Before warning
        val phase1Timestamp = System.currentTimeMillis() - 1000
        IdlePersistence.recordInteraction(context, phase1Timestamp)
        idleDetector.checkIdleState()

        assertEquals(0, warningCallbackCount)
        assertEquals(0, idleCallbackCount)
        assertFalse(idleDetector.timerState.value.isInWarningPeriod)

        // Phase 2: In warning period
        val phase2Timestamp = System.currentTimeMillis() - 1700
        IdlePersistence.recordInteraction(context, phase2Timestamp)
        idleDetector.checkIdleState()

        assertEquals(1, warningCallbackCount)
        assertEquals(0, idleCallbackCount)
        assertTrue(idleDetector.timerState.value.isInWarningPeriod)

        // Phase 3: Idle
        val phase3Timestamp = System.currentTimeMillis() - 2100
        IdlePersistence.recordInteraction(context, phase3Timestamp)
        idleDetector.checkIdleState()

        assertEquals(1, warningCallbackCount) // No additional warning
        assertEquals(1, idleCallbackCount)
        assertTrue(idleDetector.timerState.value.isIdle)

        idleDetector.cleanUp()
    }

    @Test
    fun `user interaction during warning period prevents idle`() = runTest {
        val idleTimeout = 2.seconds
        val warningTimeout = 500.milliseconds
        val idleDetector = createIdleDetector(idleTimeout, warningTimeout)

        // Initialize
        lifecycleOwner.moveToState(Lifecycle.State.CREATED)
        shadowOf(Looper.getMainLooper()).idle()
        lifecycleOwner.moveToState(Lifecycle.State.RESUMED)
        shadowOf(Looper.getMainLooper()).idle()

        // Enter warning period
        val warningTimestamp = System.currentTimeMillis() - 1700
        IdlePersistence.recordInteraction(context, warningTimestamp)
        idleDetector.checkIdleState()

        assertEquals(1, warningCallbackCount)
        assertTrue(idleDetector.timerState.value.isInWarningPeriod)

        // User interacts
        idleDetector.registerInteraction()

        // Verify state reset
        assertFalse(idleDetector.timerState.value.isInWarningPeriod)
        assertFalse(idleDetector.timerState.value.isIdle)
        assertEquals(0, idleCallbackCount) // Never went idle

        idleDetector.cleanUp()
    }

    @Test
    fun `multiple warning cycles work correctly`() = runTest {
        val idleTimeout = 2.seconds
        val warningTimeout = 500.milliseconds
        val idleDetector = createIdleDetector(idleTimeout, warningTimeout)

        // Initialize
        lifecycleOwner.moveToState(Lifecycle.State.CREATED)
        shadowOf(Looper.getMainLooper()).idle()
        lifecycleOwner.moveToState(Lifecycle.State.RESUMED)
        shadowOf(Looper.getMainLooper()).idle()

        // Cycle 1: Warning
        val cycle1Warning = System.currentTimeMillis() - 1700
        IdlePersistence.recordInteraction(context, cycle1Warning)
        idleDetector.checkIdleState()
        assertEquals(1, warningCallbackCount)

        // Cycle 1: User interacts
        idleDetector.registerInteraction()

        // Cycle 2: Warning again
        val cycle2Warning = System.currentTimeMillis() - 1700
        IdlePersistence.recordInteraction(context, cycle2Warning)
        idleDetector.checkIdleState()
        assertEquals(2, warningCallbackCount)

        // Cycle 2: User interacts again
        idleDetector.registerInteraction()

        // Cycle 3: Warning and then idle
        val cycle3Warning = System.currentTimeMillis() - 1700
        IdlePersistence.recordInteraction(context, cycle3Warning)
        idleDetector.checkIdleState()
        assertEquals(3, warningCallbackCount)

        val cycle3Idle = System.currentTimeMillis() - 2100
        IdlePersistence.recordInteraction(context, cycle3Idle)
        idleDetector.checkIdleState()
        assertEquals(3, warningCallbackCount) // Same
        assertEquals(1, idleCallbackCount) // Now idle

        idleDetector.cleanUp()
    }

    // endregion

    // region Edge Cases

    @Test
    fun `warning threshold exactly at boundary`() = runTest {
        val idleTimeout = 2.seconds
        val warningTimeout = 500.milliseconds
        val idleDetector = createIdleDetector(idleTimeout, warningTimeout)

        // Initialize
        lifecycleOwner.moveToState(Lifecycle.State.CREATED)
        shadowOf(Looper.getMainLooper()).idle()
        lifecycleOwner.moveToState(Lifecycle.State.RESUMED)
        shadowOf(Looper.getMainLooper()).idle()

        // Set exactly at warning threshold (1500ms elapsed = 500ms remaining = warningTimeout)
        val boundaryTimestamp = System.currentTimeMillis() - 1500
        IdlePersistence.recordInteraction(context, boundaryTimestamp)
        idleDetector.checkIdleState()

        // Should be in warning period at exactly the threshold
        assertTrue(idleDetector.timerState.value.isInWarningPeriod)
        assertEquals(1, warningCallbackCount)

        idleDetector.cleanUp()
    }

    @Test
    fun `very short warning period`() = runTest {
        val idleTimeout = 1.seconds
        val warningTimeout = 100.milliseconds
        val idleDetector = createIdleDetector(idleTimeout, warningTimeout)

        // Initialize
        lifecycleOwner.moveToState(Lifecycle.State.CREATED)
        shadowOf(Looper.getMainLooper()).idle()
        lifecycleOwner.moveToState(Lifecycle.State.RESUMED)
        shadowOf(Looper.getMainLooper()).idle()

        // Enter short warning period
        val warningTimestamp = System.currentTimeMillis() - 950
        IdlePersistence.recordInteraction(context, warningTimestamp)
        idleDetector.checkIdleState()

        assertTrue(idleDetector.timerState.value.isInWarningPeriod)

        idleDetector.cleanUp()
    }

    @Test
    fun `timer state with no warning threshold has null warning properties`() = runTest {
        val idleTimeout = 2.seconds
        val idleDetector = createIdleDetector(idleTimeout, warningTimeout = null)

        // Initialize
        lifecycleOwner.moveToState(Lifecycle.State.CREATED)
        shadowOf(Looper.getMainLooper()).idle()
        lifecycleOwner.moveToState(Lifecycle.State.RESUMED)
        shadowOf(Looper.getMainLooper()).idle()

        val timerState = idleDetector.timerState.value

        assertNull(timerState.warningThreshold)
        assertNull(timerState.timeUntilWarning)
        assertNull(timerState.warningProgress)
        assertFalse(timerState.isInWarningPeriod)

        idleDetector.cleanUp()
    }

    // endregion
}
