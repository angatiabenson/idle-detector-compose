package ke.co.banit.idle_detector_compose

import org.junit.Assert.*
import org.junit.Test
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
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
 * Comprehensive test suite for [IdleTimerState].
 *
 * Tests timer state creation, progress calculation, formatted time output,
 * warning period detection, and various edge cases.
 */
class IdleTimerStateTest {

    // region Initial State Tests

    @Test
    fun `initial state has full remaining time`() {
        val timeout = 5.minutes
        val state = IdleTimerState.initial(timeout)

        assertEquals(timeout, state.remainingTime)
        assertEquals(timeout, state.totalTimeout)
        assertFalse(state.isIdle)
        assertFalse(state.isInWarningPeriod)
        assertNull(state.warningThreshold)
    }

    @Test
    fun `initial state with warning threshold`() {
        val timeout = 5.minutes
        val warning = 1.minutes
        val state = IdleTimerState.initial(timeout, warning)

        assertEquals(timeout, state.remainingTime)
        assertEquals(timeout, state.totalTimeout)
        assertEquals(warning, state.warningThreshold)
        assertFalse(state.isIdle)
        assertFalse(state.isInWarningPeriod)
    }

    @Test
    fun `initial state has zero progress`() {
        val state = IdleTimerState.initial(5.minutes)

        assertEquals(0f, state.progress, 0.001f)
        assertEquals(100, state.remainingPercentage)
    }

    // endregion

    // region Idle State Tests

    @Test
    fun `idle state has zero remaining time`() {
        val timeout = 5.minutes
        val state = IdleTimerState.idle(timeout)

        assertEquals(ZERO, state.remainingTime)
        assertEquals(timeout, state.totalTimeout)
        assertTrue(state.isIdle)
    }

    @Test
    fun `idle state has full progress`() {
        val state = IdleTimerState.idle(5.minutes)

        assertEquals(1f, state.progress, 0.001f)
        assertEquals(0, state.remainingPercentage)
    }

    @Test
    fun `idle state with warning threshold marks warning period`() {
        val state = IdleTimerState.idle(5.minutes, 1.minutes)

        assertTrue(state.isIdle)
        assertTrue(state.isInWarningPeriod)
    }

    // endregion

    // region FromElapsed Factory Tests

    @Test
    fun `fromElapsed with zero elapsed time`() {
        val timeout = 5.minutes
        val state = IdleTimerState.fromElapsed(ZERO, timeout)

        assertEquals(timeout, state.remainingTime)
        assertFalse(state.isIdle)
        assertEquals(0f, state.progress, 0.001f)
    }

    @Test
    fun `fromElapsed with half elapsed time`() {
        val timeout = 10.minutes
        val elapsed = 5.minutes
        val state = IdleTimerState.fromElapsed(elapsed, timeout)

        assertEquals(5.minutes, state.remainingTime)
        assertFalse(state.isIdle)
        assertEquals(0.5f, state.progress, 0.001f)
        assertEquals(50, state.remainingPercentage)
    }

    @Test
    fun `fromElapsed with full elapsed time marks idle`() {
        val timeout = 5.minutes
        val state = IdleTimerState.fromElapsed(timeout, timeout)

        assertEquals(ZERO, state.remainingTime)
        assertTrue(state.isIdle)
        assertEquals(1f, state.progress, 0.001f)
    }

    @Test
    fun `fromElapsed with exceeded elapsed time clamps to zero`() {
        val timeout = 5.minutes
        val elapsed = 10.minutes
        val state = IdleTimerState.fromElapsed(elapsed, timeout)

        assertEquals(ZERO, state.remainingTime)
        assertTrue(state.isIdle)
        assertEquals(1f, state.progress, 0.001f)
    }

    @Test
    fun `fromElapsed enters warning period correctly`() {
        val timeout = 5.minutes
        val warning = 1.minutes
        val elapsed = 4.minutes + 30.seconds  // 30 seconds remaining

        val state = IdleTimerState.fromElapsed(elapsed, timeout, warning)

        assertEquals(30.seconds, state.remainingTime)
        assertFalse(state.isIdle)
        assertTrue(state.isInWarningPeriod)
    }

    @Test
    fun `fromElapsed before warning period`() {
        val timeout = 5.minutes
        val warning = 1.minutes
        val elapsed = 3.minutes  // 2 minutes remaining, not in warning

        val state = IdleTimerState.fromElapsed(elapsed, timeout, warning)

        assertEquals(2.minutes, state.remainingTime)
        assertFalse(state.isIdle)
        assertFalse(state.isInWarningPeriod)
    }

    @Test
    fun `fromElapsed at exact warning threshold`() {
        val timeout = 5.minutes
        val warning = 1.minutes
        val elapsed = 4.minutes  // Exactly at warning threshold

        val state = IdleTimerState.fromElapsed(elapsed, timeout, warning)

        assertEquals(1.minutes, state.remainingTime)
        assertFalse(state.isIdle)
        assertTrue(state.isInWarningPeriod)
    }

    // endregion

    // region Progress Calculation Tests

    @Test
    fun `progress at 25 percent`() {
        val state = IdleTimerState(
            remainingTime = 75.seconds,
            totalTimeout = 100.seconds,
        )

        assertEquals(0.25f, state.progress, 0.001f)
        assertEquals(75, state.remainingPercentage)
    }

    @Test
    fun `progress at 75 percent`() {
        val state = IdleTimerState(
            remainingTime = 25.seconds,
            totalTimeout = 100.seconds,
        )

        assertEquals(0.75f, state.progress, 0.001f)
        assertEquals(25, state.remainingPercentage)
    }

    @Test
    fun `progress with zero timeout returns zero`() {
        val state = IdleTimerState(
            remainingTime = ZERO,
            totalTimeout = ZERO,
        )

        assertEquals(0f, state.progress, 0.001f)
    }

    @Test
    fun `progress is clamped to valid range`() {
        // Even with weird values, progress should be 0-1
        val state = IdleTimerState(
            remainingTime = (-10).seconds,  // Negative remaining (shouldn't happen but test edge case)
            totalTimeout = 100.seconds,
        )

        assertTrue(state.progress >= 0f)
        assertTrue(state.progress <= 1f)
    }

    // endregion

    // region Formatted Time Tests

    @Test
    fun `formattedRemainingTime with minutes and seconds`() {
        val state = IdleTimerState(
            remainingTime = 4.minutes + 30.seconds,
            totalTimeout = 5.minutes,
        )

        assertEquals("04:30", state.formattedRemainingTime)
    }

    @Test
    fun `formattedRemainingTime with only seconds`() {
        val state = IdleTimerState(
            remainingTime = 45.seconds,
            totalTimeout = 1.minutes,
        )

        assertEquals("00:45", state.formattedRemainingTime)
    }

    @Test
    fun `formattedRemainingTime with zero`() {
        val state = IdleTimerState(
            remainingTime = ZERO,
            totalTimeout = 5.minutes,
        )

        assertEquals("00:00", state.formattedRemainingTime)
    }

    @Test
    fun `formattedRemainingTime with large value`() {
        val state = IdleTimerState(
            remainingTime = 59.minutes + 59.seconds,
            totalTimeout = 1.hours,
        )

        assertEquals("59:59", state.formattedRemainingTime)
    }

    @Test
    fun `formattedRemainingTimeLong with hours`() {
        val state = IdleTimerState(
            remainingTime = 1.hours + 30.minutes + 45.seconds,
            totalTimeout = 2.hours,
        )

        assertEquals("01:30:45", state.formattedRemainingTimeLong)
    }

    @Test
    fun `formattedRemainingTimeLong without hours shows MM SS`() {
        val state = IdleTimerState(
            remainingTime = 30.minutes + 45.seconds,
            totalTimeout = 1.hours,
        )

        assertEquals("30:45", state.formattedRemainingTimeLong)
    }

    @Test
    fun `formattedRemainingTimeLong with multiple hours`() {
        val state = IdleTimerState(
            remainingTime = 12.hours + 5.minutes + 3.seconds,
            totalTimeout = 24.hours,
        )

        assertEquals("12:05:03", state.formattedRemainingTimeLong)
    }

    // endregion

    // region Elapsed Time Tests

    @Test
    fun `elapsedTime calculation`() {
        val state = IdleTimerState(
            remainingTime = 3.minutes,
            totalTimeout = 5.minutes,
        )

        assertEquals(2.minutes, state.elapsedTime)
    }

    @Test
    fun `elapsedTime at start is zero`() {
        val state = IdleTimerState.initial(5.minutes)

        assertEquals(ZERO, state.elapsedTime)
    }

    @Test
    fun `elapsedTime at end equals timeout`() {
        val state = IdleTimerState.idle(5.minutes)

        assertEquals(5.minutes, state.elapsedTime)
    }

    // endregion

    // region Warning Period Tests

    @Test
    fun `timeUntilWarning returns null without warning threshold`() {
        val state = IdleTimerState(
            remainingTime = 3.minutes,
            totalTimeout = 5.minutes,
            warningThreshold = null,
        )

        assertNull(state.timeUntilWarning)
    }

    @Test
    fun `timeUntilWarning before warning period`() {
        val state = IdleTimerState(
            remainingTime = 3.minutes,  // 3 min remaining
            totalTimeout = 5.minutes,
            warningThreshold = 1.minutes,  // Warning at 1 min remaining
        )

        assertEquals(2.minutes, state.timeUntilWarning)
    }

    @Test
    fun `timeUntilWarning during warning period is zero`() {
        val state = IdleTimerState(
            remainingTime = 30.seconds,  // In warning period
            totalTimeout = 5.minutes,
            warningThreshold = 1.minutes,
            isInWarningPeriod = true,
        )

        assertEquals(ZERO, state.timeUntilWarning)
    }

    @Test
    fun `warningProgress returns null without warning threshold`() {
        val state = IdleTimerState(
            remainingTime = 30.seconds,
            totalTimeout = 5.minutes,
            warningThreshold = null,
        )

        assertNull(state.warningProgress)
    }

    @Test
    fun `warningProgress returns null before warning period`() {
        val state = IdleTimerState(
            remainingTime = 3.minutes,
            totalTimeout = 5.minutes,
            warningThreshold = 1.minutes,
            isInWarningPeriod = false,
        )

        assertNull(state.warningProgress)
    }

    @Test
    fun `warningProgress at start of warning period is zero`() {
        val state = IdleTimerState(
            remainingTime = 1.minutes,  // Just entered warning
            totalTimeout = 5.minutes,
            warningThreshold = 1.minutes,
            isInWarningPeriod = true,
        )

        assertEquals(0f, state.warningProgress!!, 0.001f)
    }

    @Test
    fun `warningProgress at middle of warning period`() {
        val state = IdleTimerState(
            remainingTime = 30.seconds,  // Halfway through 1 minute warning
            totalTimeout = 5.minutes,
            warningThreshold = 1.minutes,
            isInWarningPeriod = true,
        )

        assertEquals(0.5f, state.warningProgress!!, 0.001f)
    }

    @Test
    fun `warningProgress at end of warning period`() {
        val state = IdleTimerState(
            remainingTime = ZERO,
            totalTimeout = 5.minutes,
            warningThreshold = 1.minutes,
            isInWarningPeriod = true,
            isIdle = true,
        )

        assertEquals(1f, state.warningProgress!!, 0.001f)
    }

    // endregion

    // region Extension Function Tests

    @Test
    fun `isCloseTo returns true when close to idle`() {
        val state = IdleTimerState(
            remainingTime = 5.seconds,
            totalTimeout = 1.minutes,
        )

        assertTrue(state.isCloseTo(10.seconds))
    }

    @Test
    fun `isCloseTo returns false when not close`() {
        val state = IdleTimerState(
            remainingTime = 30.seconds,
            totalTimeout = 1.minutes,
        )

        assertFalse(state.isCloseTo(10.seconds))
    }

    @Test
    fun `isCloseTo returns false when idle`() {
        val state = IdleTimerState(
            remainingTime = ZERO,
            totalTimeout = 1.minutes,
            isIdle = true,
        )

        assertFalse(state.isCloseTo(10.seconds))
    }

    @Test
    fun `remainingPercentage calculation`() {
        val state = IdleTimerState(
            remainingTime = 75.seconds,
            totalTimeout = 100.seconds,
        )

        assertEquals(75, state.remainingPercentage)
    }

    @Test
    fun `remainingPercentage at zero`() {
        val state = IdleTimerState.idle(5.minutes)

        assertEquals(0, state.remainingPercentage)
    }

    @Test
    fun `remainingPercentage at full`() {
        val state = IdleTimerState.initial(5.minutes)

        assertEquals(100, state.remainingPercentage)
    }

    // endregion

    // region Data Class Functionality Tests

    @Test
    fun `equality test for same values`() {
        val state1 = IdleTimerState(
            remainingTime = 3.minutes,
            totalTimeout = 5.minutes,
            warningThreshold = 1.minutes,
            isIdle = false,
            isInWarningPeriod = false,
        )
        val state2 = IdleTimerState(
            remainingTime = 3.minutes,
            totalTimeout = 5.minutes,
            warningThreshold = 1.minutes,
            isIdle = false,
            isInWarningPeriod = false,
        )

        assertEquals(state1, state2)
    }

    @Test
    fun `inequality test for different remaining time`() {
        val state1 = IdleTimerState.initial(5.minutes)
        val state2 = IdleTimerState(
            remainingTime = 4.minutes,
            totalTimeout = 5.minutes,
        )

        assertNotEquals(state1, state2)
    }

    @Test
    fun `copy function works correctly`() {
        val original = IdleTimerState.initial(5.minutes, 1.minutes)
        val copied = original.copy(isInWarningPeriod = true)

        assertEquals(original.remainingTime, copied.remainingTime)
        assertEquals(original.totalTimeout, copied.totalTimeout)
        assertFalse(original.isInWarningPeriod)
        assertTrue(copied.isInWarningPeriod)
    }

    // endregion

    // region Edge Cases

    @Test
    fun `very short timeout`() {
        val state = IdleTimerState.initial(100.milliseconds)

        assertEquals(100.milliseconds, state.remainingTime)
        assertEquals("00:00", state.formattedRemainingTime)  // Less than 1 second
    }

    @Test
    fun `very long timeout`() {
        val timeout = 24.hours
        val state = IdleTimerState.initial(timeout)

        assertEquals(timeout, state.remainingTime)
        assertEquals("24:00:00", state.formattedRemainingTimeLong)
    }

    @Test
    fun `warning threshold equal to remaining time`() {
        val state = IdleTimerState.fromElapsed(
            elapsedTime = 4.minutes,
            timeout = 5.minutes,
            warningThreshold = 1.minutes,
        )

        assertTrue(state.isInWarningPeriod)
        assertEquals(1.minutes, state.remainingTime)
    }

    @Test
    fun `millisecond precision in progress`() {
        val state = IdleTimerState(
            remainingTime = 999.milliseconds,
            totalTimeout = 1000.milliseconds,
        )

        assertEquals(0.001f, state.progress, 0.0001f)
    }

    // endregion
}
