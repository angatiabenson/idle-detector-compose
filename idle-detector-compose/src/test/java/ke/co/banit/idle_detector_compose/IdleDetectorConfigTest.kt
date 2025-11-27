package ke.co.banit.idle_detector_compose

import org.junit.Assert.*
import org.junit.Test
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
 * Test suite for [IdleDetectorConfig] and [IdleDetectorConfig.Builder].
 *
 * Verifies configuration builder pattern, validation logic, and default values.
 */
class IdleDetectorConfigTest {

    @Test
    fun `test builder with minimum required parameters`() {
        val config = IdleDetectorConfig.Builder()
            .timeout(5.minutes)
            .build()

        assertEquals("Timeout should match", 5.minutes, config.timeout)
        assertEquals("Default check interval should be 1 second", 1.seconds, config.checkInterval)
        assertTrue("Background detection should be enabled by default", config.enableBackgroundDetection)
        assertFalse("Logging should be disabled by default", config.enableLogging)
        assertTrue("Interaction debouncing should be enabled by default", config.debounceInteractions)
        assertEquals("Default interaction debounce window", 100.milliseconds, config.interactionDebounceWindow)
        assertEquals("Default persistence debounce window", 500.milliseconds, config.persistenceDebounceWindow)
    }

    @Test
    fun `test builder with all parameters customized`() {
        val config = IdleDetectorConfig.Builder()
            .timeout(10.minutes)
            .checkInterval(2.seconds)
            .disableBackgroundDetection()
            .enableLogging()
            .disableInteractionDebounce()
            .interactionDebounce(50.milliseconds)
            .persistenceDebounce(1.seconds)
            .build()

        assertEquals("Timeout should match", 10.minutes, config.timeout)
        assertEquals("Check interval should match", 2.seconds, config.checkInterval)
        assertFalse("Background detection should be disabled", config.enableBackgroundDetection)
        assertTrue("Logging should be enabled", config.enableLogging)
        assertFalse("Interaction debouncing should be disabled", config.debounceInteractions)
        assertEquals("Interaction debounce window should match", 50.milliseconds, config.interactionDebounceWindow)
        assertEquals("Persistence debounce window should match", 1.seconds, config.persistenceDebounceWindow)
    }

    @Test
    fun `test enabling background detection`() {
        val config = IdleDetectorConfig.Builder()
            .timeout(5.minutes)
            .enableBackgroundDetection()
            .build()

        assertTrue("Background detection should be enabled", config.enableBackgroundDetection)
    }

    @Test
    fun `test disabling background detection`() {
        val config = IdleDetectorConfig.Builder()
            .timeout(5.minutes)
            .disableBackgroundDetection()
            .build()

        assertFalse("Background detection should be disabled", config.enableBackgroundDetection)
    }

    @Test
    fun `test enabling logging`() {
        val config = IdleDetectorConfig.Builder()
            .timeout(5.minutes)
            .enableLogging()
            .build()

        assertTrue("Logging should be enabled", config.enableLogging)
    }

    @Test
    fun `test enabling interaction debounce`() {
        val config = IdleDetectorConfig.Builder()
            .timeout(5.minutes)
            .enableInteractionDebounce()
            .build()

        assertTrue("Interaction debouncing should be enabled", config.debounceInteractions)
    }

    @Test
    fun `test disabling interaction debounce`() {
        val config = IdleDetectorConfig.Builder()
            .timeout(5.minutes)
            .disableInteractionDebounce()
            .build()

        assertFalse("Interaction debouncing should be disabled", config.debounceInteractions)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `test build without timeout throws exception`() {
        IdleDetectorConfig.Builder()
            .build()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `test negative timeout throws exception`() {
        IdleDetectorConfig.Builder()
            .timeout((-5).seconds)
            .build()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `test zero timeout throws exception`() {
        IdleDetectorConfig.Builder()
            .timeout(0.seconds)
            .build()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `test negative check interval throws exception`() {
        IdleDetectorConfig.Builder()
            .timeout(5.minutes)
            .checkInterval((-1).seconds)
            .build()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `test zero check interval throws exception`() {
        IdleDetectorConfig.Builder()
            .timeout(5.minutes)
            .checkInterval(0.seconds)
            .build()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `test check interval greater than timeout throws exception`() {
        IdleDetectorConfig.Builder()
            .timeout(1.seconds)
            .checkInterval(2.seconds)
            .build()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `test check interval equal to timeout throws exception`() {
        IdleDetectorConfig.Builder()
            .timeout(1.seconds)
            .checkInterval(1.seconds)
            .build()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `test negative interaction debounce window throws exception`() {
        IdleDetectorConfig.Builder()
            .timeout(5.minutes)
            .interactionDebounce((-100).milliseconds)
            .build()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `test negative persistence debounce window throws exception`() {
        IdleDetectorConfig.Builder()
            .timeout(5.minutes)
            .persistenceDebounce((-500).milliseconds)
            .build()
    }

    @Test
    fun `test zero debounce windows are valid`() {
        val config = IdleDetectorConfig.Builder()
            .timeout(5.minutes)
            .interactionDebounce(0.milliseconds)
            .persistenceDebounce(0.milliseconds)
            .build()

        assertEquals("Zero interaction debounce should be valid", 0.milliseconds, config.interactionDebounceWindow)
        assertEquals("Zero persistence debounce should be valid", 0.milliseconds, config.persistenceDebounceWindow)
    }

    @Test
    fun `test builder method chaining`() {
        val config = IdleDetectorConfig.Builder()
            .timeout(5.minutes)
            .checkInterval(1.seconds)
            .enableLogging()
            .disableBackgroundDetection()
            .disableInteractionDebounce()
            .interactionDebounce(50.milliseconds)
            .persistenceDebounce(1.seconds)
            .build()

        assertNotNull("Config should be built successfully", config)
    }

    @Test
    fun `test companion builder factory method`() {
        val config = IdleDetectorConfig.builder()
            .timeout(5.minutes)
            .build()

        assertNotNull("Builder factory method should work", config)
        assertEquals("Timeout should match", 5.minutes, config.timeout)
    }

    @Test
    fun `test config immutability via data class`() {
        val config = IdleDetectorConfig.Builder()
            .timeout(5.minutes)
            .build()

        // Config is immutable, we can create a copy with modified values
        val modifiedConfig = config.copy(timeout = 10.minutes)

        assertEquals("Original timeout should be unchanged", 5.minutes, config.timeout)
        assertEquals("Modified timeout should be changed", 10.minutes, modifiedConfig.timeout)
    }

    @Test
    fun `test config equality`() {
        val config1 = IdleDetectorConfig.Builder()
            .timeout(5.minutes)
            .checkInterval(1.seconds)
            .build()

        val config2 = IdleDetectorConfig.Builder()
            .timeout(5.minutes)
            .checkInterval(1.seconds)
            .build()

        assertEquals("Configs with same values should be equal", config1, config2)
    }

    @Test
    fun `test config inequality when timeout differs`() {
        val config1 = IdleDetectorConfig.Builder()
            .timeout(5.minutes)
            .build()

        val config2 = IdleDetectorConfig.Builder()
            .timeout(10.minutes)
            .build()

        assertNotEquals("Configs with different timeouts should not be equal", config1, config2)
    }

    @Test
    fun `test config inequality when check interval differs`() {
        val config1 = IdleDetectorConfig.Builder()
            .timeout(5.minutes)
            .checkInterval(1.seconds)
            .build()

        val config2 = IdleDetectorConfig.Builder()
            .timeout(5.minutes)
            .checkInterval(2.seconds)
            .build()

        assertNotEquals("Configs with different check intervals should not be equal", config1, config2)
    }

    @Test
    fun `test realistic timeout values`() {
        val configs = listOf(
            IdleDetectorConfig.Builder().timeout(30.seconds).build(),
            IdleDetectorConfig.Builder().timeout(1.minutes).build(),
            IdleDetectorConfig.Builder().timeout(5.minutes).build(),
            IdleDetectorConfig.Builder().timeout(15.minutes).build(),
            IdleDetectorConfig.Builder().timeout(30.minutes).build(),
        )

        configs.forEach { config ->
            assertNotNull("Config should be valid", config)
            assertTrue("Timeout should be positive", config.timeout.inWholeMilliseconds > 0)
        }
    }

    @Test
    fun `test realistic check interval values`() {
        val config500ms = IdleDetectorConfig.Builder()
            .timeout(5.minutes)
            .checkInterval(500.milliseconds)
            .build()

        val config1s = IdleDetectorConfig.Builder()
            .timeout(5.minutes)
            .checkInterval(1.seconds)
            .build()

        val config2s = IdleDetectorConfig.Builder()
            .timeout(5.minutes)
            .checkInterval(2.seconds)
            .build()

        val config5s = IdleDetectorConfig.Builder()
            .timeout(10.minutes)
            .checkInterval(5.seconds)
            .build()

        assertEquals(500.milliseconds, config500ms.checkInterval)
        assertEquals(1.seconds, config1s.checkInterval)
        assertEquals(2.seconds, config2s.checkInterval)
        assertEquals(5.seconds, config5s.checkInterval)
    }

    @Test
    fun `test config toString contains key information`() {
        val config = IdleDetectorConfig.Builder()
            .timeout(5.minutes)
            .checkInterval(1.seconds)
            .enableLogging()
            .build()

        val configString = config.toString()

        assertTrue("toString should contain timeout info", configString.contains("timeout"))
        assertTrue("toString should contain check interval info", configString.contains("checkInterval"))
    }

    @Test
    fun `test toggling background detection`() {
        val configEnabled = IdleDetectorConfig.Builder()
            .timeout(5.minutes)
            .enableBackgroundDetection()
            .build()

        val configDisabled = IdleDetectorConfig.Builder()
            .timeout(5.minutes)
            .disableBackgroundDetection()
            .build()

        assertTrue("First config should have background detection enabled", configEnabled.enableBackgroundDetection)
        assertFalse("Second config should have background detection disabled", configDisabled.enableBackgroundDetection)
    }

    @Test
    fun `test very short timeout with appropriate check interval`() {
        val config = IdleDetectorConfig.Builder()
            .timeout(5.seconds)
            .checkInterval(500.milliseconds)
            .build()

        assertEquals("Timeout should be 5 seconds", 5.seconds, config.timeout)
        assertEquals("Check interval should be 500ms", 500.milliseconds, config.checkInterval)
    }

    @Test
    fun `test very long timeout with appropriate check interval`() {
        val config = IdleDetectorConfig.Builder()
            .timeout(60.minutes)
            .checkInterval(10.seconds)
            .build()

        assertEquals("Timeout should be 60 minutes", 60.minutes, config.timeout)
        assertEquals("Check interval should be 10 seconds", 10.seconds, config.checkInterval)
    }
}
