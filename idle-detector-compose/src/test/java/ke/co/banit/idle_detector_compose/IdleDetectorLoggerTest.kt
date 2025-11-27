package ke.co.banit.idle_detector_compose

import android.util.Log
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLog

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
 * Test suite for [IdleDetectorLogger].
 *
 * Verifies logging behavior, configuration, and that logging can be enabled/disabled.
 * Uses Robolectric to test Android Log class integration.
 */
@RunWith(RobolectricTestRunner::class)
class IdleDetectorLoggerTest {

    private val originalIsEnabled = IdleDetectorLogger.isEnabled
    private val originalTag = IdleDetectorLogger.tag

    @Before
    fun setUp() {
        // Enable shadow logging for Robolectric
        ShadowLog.stream = System.out

        // Reset logger to default state
        IdleDetectorLogger.isEnabled = false
        IdleDetectorLogger.tag = "IdleDetector"

        // Clear any previous logs
        ShadowLog.reset()
    }

    @After
    fun tearDown() {
        // Restore original values
        IdleDetectorLogger.isEnabled = originalIsEnabled
        IdleDetectorLogger.tag = originalTag
        ShadowLog.reset()
    }

    @Test
    fun `test logging is disabled by default`() {
        assertFalse(
            "Logging should be disabled by default",
            IdleDetectorLogger.isEnabled
        )
    }

    @Test
    fun `test default tag is IdleDetector`() {
        assertEquals(
            "Default tag should be 'IdleDetector'",
            "IdleDetector",
            IdleDetectorLogger.tag
        )
    }

    @Test
    fun `test debug log when disabled produces no output`() {
        IdleDetectorLogger.isEnabled = false

        IdleDetectorLogger.d("Test debug message")

        val logs = ShadowLog.getLogs()
        assertTrue(
            "No logs should be produced when logging is disabled",
            logs.isEmpty()
        )
    }

    @Test
    fun `test debug log when enabled produces output`() {
        IdleDetectorLogger.isEnabled = true

        IdleDetectorLogger.d("Test debug message")

        val logs = ShadowLog.getLogs()
        assertFalse("Logs should be produced when logging is enabled", logs.isEmpty())

        val logEntry = logs.first()
        assertEquals("Log type should be DEBUG", Log.DEBUG, logEntry.type)
        assertEquals("Log tag should match configured tag", "IdleDetector", logEntry.tag)
        assertEquals("Log message should match", "Test debug message", logEntry.msg)
    }

    @Test
    fun `test error log when enabled`() {
        IdleDetectorLogger.isEnabled = true

        IdleDetectorLogger.e("Test error message")

        val logs = ShadowLog.getLogs()
        val logEntry = logs.first()
        assertEquals("Log type should be ERROR", Log.ERROR, logEntry.type)
        assertEquals("Log message should match", "Test error message", logEntry.msg)
    }

    @Test
    fun `test error log with throwable`() {
        IdleDetectorLogger.isEnabled = true

        val exception = RuntimeException("Test exception")
        IdleDetectorLogger.e("Error occurred", exception)

        val logs = ShadowLog.getLogs()
        val logEntry = logs.first()
        assertEquals("Log type should be ERROR", Log.ERROR, logEntry.type)
        assertEquals("Log message should match", "Error occurred", logEntry.msg)
        assertEquals("Throwable should be attached", exception, logEntry.throwable)
    }

    @Test
    fun `test warning log when enabled`() {
        IdleDetectorLogger.isEnabled = true

        IdleDetectorLogger.w("Test warning message")

        val logs = ShadowLog.getLogs()
        val logEntry = logs.first()
        assertEquals("Log type should be WARN", Log.WARN, logEntry.type)
        assertEquals("Log message should match", "Test warning message", logEntry.msg)
    }

    @Test
    fun `test info log when enabled`() {
        IdleDetectorLogger.isEnabled = true

        IdleDetectorLogger.i("Test info message")

        val logs = ShadowLog.getLogs()
        val logEntry = logs.first()
        assertEquals("Log type should be INFO", Log.INFO, logEntry.type)
        assertEquals("Log message should match", "Test info message", logEntry.msg)
    }

    @Test
    fun `test custom tag is used`() {
        IdleDetectorLogger.isEnabled = true
        IdleDetectorLogger.tag = "CustomTag"

        IdleDetectorLogger.d("Test message")

        val logs = ShadowLog.getLogs()
        val logEntry = logs.first()
        assertEquals("Custom tag should be used", "CustomTag", logEntry.tag)
    }

    @Test
    fun `test multiple log calls`() {
        IdleDetectorLogger.isEnabled = true

        IdleDetectorLogger.d("Debug message")
        IdleDetectorLogger.i("Info message")
        IdleDetectorLogger.w("Warning message")
        IdleDetectorLogger.e("Error message")

        val logs = ShadowLog.getLogs()
        assertEquals("Should have 4 log entries", 4, logs.size)

        assertEquals("First should be DEBUG", Log.DEBUG, logs[0].type)
        assertEquals("Second should be INFO", Log.INFO, logs[1].type)
        assertEquals("Third should be WARN", Log.WARN, logs[2].type)
        assertEquals("Fourth should be ERROR", Log.ERROR, logs[3].type)
    }

    @Test
    fun `test enabling and disabling logging`() {
        // Start disabled
        IdleDetectorLogger.isEnabled = false
        IdleDetectorLogger.d("Should not appear")

        var logs = ShadowLog.getLogs()
        assertTrue("No logs when disabled", logs.isEmpty())

        // Enable
        IdleDetectorLogger.isEnabled = true
        IdleDetectorLogger.d("Should appear")

        logs = ShadowLog.getLogs()
        assertEquals("One log when enabled", 1, logs.size)

        // Disable again
        ShadowLog.reset()
        IdleDetectorLogger.isEnabled = false
        IdleDetectorLogger.d("Should not appear again")

        logs = ShadowLog.getLogs()
        assertTrue("No new logs when disabled again", logs.isEmpty())
    }

    @Test
    fun `test logging with empty message`() {
        IdleDetectorLogger.isEnabled = true

        IdleDetectorLogger.d("")

        val logs = ShadowLog.getLogs()
        assertEquals("Should log empty message", 1, logs.size)
        assertEquals("Message should be empty", "", logs.first().msg)
    }

    @Test
    fun `test logging with special characters`() {
        IdleDetectorLogger.isEnabled = true

        val specialMessage = "Test with special chars: !@#$%^&*()_+-={}[]|\\:\";<>?,./~`"
        IdleDetectorLogger.d(specialMessage)

        val logs = ShadowLog.getLogs()
        assertEquals("Special characters should be preserved", specialMessage, logs.first().msg)
    }

    @Test
    fun `test logging with newlines`() {
        IdleDetectorLogger.isEnabled = true

        val multilineMessage = "Line 1\nLine 2\nLine 3"
        IdleDetectorLogger.d(multilineMessage)

        val logs = ShadowLog.getLogs()
        assertEquals("Newlines should be preserved", multilineMessage, logs.first().msg)
    }

    @Test
    fun `test changing tag multiple times`() {
        IdleDetectorLogger.isEnabled = true

        IdleDetectorLogger.tag = "Tag1"
        IdleDetectorLogger.d("Message 1")

        IdleDetectorLogger.tag = "Tag2"
        IdleDetectorLogger.d("Message 2")

        IdleDetectorLogger.tag = "Tag3"
        IdleDetectorLogger.d("Message 3")

        val logs = ShadowLog.getLogs()
        assertEquals("Should have 3 log entries", 3, logs.size)
        assertEquals("First message should have Tag1", "Tag1", logs[0].tag)
        assertEquals("Second message should have Tag2", "Tag2", logs[1].tag)
        assertEquals("Third message should have Tag3", "Tag3", logs[2].tag)
    }
}
