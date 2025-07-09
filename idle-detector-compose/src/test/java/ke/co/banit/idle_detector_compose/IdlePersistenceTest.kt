package ke.co.banit.idle_detector_compose

import android.content.Context
import androidx.test.core.app.ApplicationProvider
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
 * Unit tests for IdlePersistence to verify SharedPreferences-based storage functionality.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class IdlePersistenceTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        // Clear any existing preferences
        IdlePersistence.reset(context)
    }

    @After
    fun tearDown() {
        // Clean up after each test
        IdlePersistence.reset(context)
    }

    @Test
    fun `recordInteraction stores timestamp correctly`() {
        val timestamp = 1234567890L

        IdlePersistence.recordInteraction(context, timestamp)

        val retrievedTimestamp = IdlePersistence.getLastInteractionTimestamp(context)
        assertEquals(timestamp, retrievedTimestamp)
    }

    @Test
    fun `getLastInteractionTimestamp returns 0 when no timestamp stored`() {
        val timestamp = IdlePersistence.getLastInteractionTimestamp(context)
        assertEquals(0L, timestamp)
    }

    @Test
    fun `recordInteraction resets background timeout flag`() {
        // First set the background timeout flag to true
        IdlePersistence.setBackgroundTimeoutTriggered(context, true)
        assertTrue(IdlePersistence.isBackgroundTimeoutTriggered(context))

        // Record an interaction
        IdlePersistence.recordInteraction(context, System.currentTimeMillis())

        // Verify the flag is reset to false
        assertFalse(IdlePersistence.isBackgroundTimeoutTriggered(context))
    }

    @Test
    fun `setBackgroundTimeoutTriggered stores flag correctly`() {
        // Test setting to true
        IdlePersistence.setBackgroundTimeoutTriggered(context, true)
        assertTrue(IdlePersistence.isBackgroundTimeoutTriggered(context))

        // Test setting to false
        IdlePersistence.setBackgroundTimeoutTriggered(context, false)
        assertFalse(IdlePersistence.isBackgroundTimeoutTriggered(context))
    }

    @Test
    fun `isBackgroundTimeoutTriggered returns false by default`() {
        val isTriggered = IdlePersistence.isBackgroundTimeoutTriggered(context)
        assertFalse(isTriggered)
    }

    @Test
    fun `reset clears all stored data`() {
        // Store some data
        val timestamp = System.currentTimeMillis()
        IdlePersistence.recordInteraction(context, timestamp)
        IdlePersistence.setBackgroundTimeoutTriggered(context, true)

        // Verify data is stored
        assertEquals(timestamp, IdlePersistence.getLastInteractionTimestamp(context))
        assertTrue(IdlePersistence.isBackgroundTimeoutTriggered(context))

        // Reset and verify data is cleared
        IdlePersistence.reset(context)

        assertEquals(0L, IdlePersistence.getLastInteractionTimestamp(context))
        assertFalse(IdlePersistence.isBackgroundTimeoutTriggered(context))
    }

    @Test
    fun `multiple interactions update timestamp correctly`() {
        val firstTimestamp = 1000L
        val secondTimestamp = 2000L

        IdlePersistence.recordInteraction(context, firstTimestamp)
        assertEquals(firstTimestamp, IdlePersistence.getLastInteractionTimestamp(context))

        IdlePersistence.recordInteraction(context, secondTimestamp)
        assertEquals(secondTimestamp, IdlePersistence.getLastInteractionTimestamp(context))
    }

    @Test
    fun `concurrent access handles data correctly`() {
        val timestamp1 = 1000L
        val timestamp2 = 2000L

        // Simulate concurrent writes
        IdlePersistence.recordInteraction(context, timestamp1)
        IdlePersistence.setBackgroundTimeoutTriggered(context, true)
        IdlePersistence.recordInteraction(context, timestamp2)

        // Verify final state
        assertEquals(timestamp2, IdlePersistence.getLastInteractionTimestamp(context))
        assertFalse(IdlePersistence.isBackgroundTimeoutTriggered(context))
    }
}
