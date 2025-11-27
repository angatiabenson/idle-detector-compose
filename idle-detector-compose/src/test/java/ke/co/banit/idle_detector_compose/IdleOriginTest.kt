package ke.co.banit.idle_detector_compose

import org.junit.Assert.*
import org.junit.Test

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
 * Test suite for [IdleOrigin] sealed interface.
 *
 * Verifies type safety, extension properties, and backward compatibility
 * with boolean-based origin flags.
 */
class IdleOriginTest {

    @Test
    fun `test Foreground origin has correct isBackground property`() {
        val origin: IdleOrigin = IdleOrigin.Foreground
        assertFalse("Foreground origin should not be from background", origin.isBackground)
        assertTrue("Foreground origin should be from foreground", origin.isForeground)
    }

    @Test
    fun `test Background origin has correct isBackground property`() {
        val origin: IdleOrigin = IdleOrigin.Background
        assertTrue("Background origin should be from background", origin.isBackground)
        assertFalse("Background origin should not be from foreground", origin.isForeground)
    }

    @Test
    fun `test fromBoolean with true returns Background`() {
        val origin = IdleOrigin.fromBoolean(true)
        assertEquals("fromBoolean(true) should return Background", IdleOrigin.Background, origin)
        assertTrue("Result should be Background instance", origin is IdleOrigin.Background)
    }

    @Test
    fun `test fromBoolean with false returns Foreground`() {
        val origin = IdleOrigin.fromBoolean(false)
        assertEquals("fromBoolean(false) should return Foreground", IdleOrigin.Foreground, origin)
        assertTrue("Result should be Foreground instance", origin is IdleOrigin.Foreground)
    }

    @Test
    fun `test when expression with IdleOrigin`() {
        val foregroundOrigin: IdleOrigin = IdleOrigin.Foreground
        val backgroundOrigin: IdleOrigin = IdleOrigin.Background

        val foregroundResult = when (foregroundOrigin) {
            is IdleOrigin.Foreground -> "foreground"
            is IdleOrigin.Background -> "background"
        }

        val backgroundResult = when (backgroundOrigin) {
            is IdleOrigin.Foreground -> "foreground"
            is IdleOrigin.Background -> "background"
        }

        assertEquals("Foreground should match in when expression", "foreground", foregroundResult)
        assertEquals("Background should match in when expression", "background", backgroundResult)
    }

    @Test
    fun `test IdleOrigin sealed interface exhaustiveness`() {
        val origins = listOf<IdleOrigin>(
            IdleOrigin.Foreground,
            IdleOrigin.Background
        )

        origins.forEach { origin ->
            // This test verifies that all cases are handled
            val handled = when (origin) {
                is IdleOrigin.Foreground -> true
                is IdleOrigin.Background -> true
            }
            assertTrue("All IdleOrigin cases should be handled", handled)
        }
    }

    @Test
    fun `test IdleOrigin equality`() {
        val foreground1: IdleOrigin = IdleOrigin.Foreground
        val foreground2: IdleOrigin = IdleOrigin.Foreground
        val background1: IdleOrigin = IdleOrigin.Background
        val background2: IdleOrigin = IdleOrigin.Background

        // Data objects have referential equality
        assertSame("Foreground instances should be same", foreground1, foreground2)
        assertSame("Background instances should be same", background1, background2)
        assertNotSame("Foreground and Background should not be same", foreground1, background1)
    }

    @Test
    fun `test backward compatibility with boolean flags`() {
        // Simulating old API usage pattern
        fun handleIdle(fromBackground: Boolean) {
            val origin = IdleOrigin.fromBoolean(fromBackground)

            // New code can use type-safe when expression
            when (origin) {
                IdleOrigin.Foreground -> {
                    assertFalse("Should be foreground", fromBackground)
                }
                IdleOrigin.Background -> {
                    assertTrue("Should be background", fromBackground)
                }
            }
        }

        handleIdle(false) // Foreground
        handleIdle(true)  // Background
    }

    @Test
    fun `test extension properties are consistent`() {
        val origins = listOf(IdleOrigin.Foreground, IdleOrigin.Background)

        origins.forEach { origin ->
            // isBackground and isForeground should be mutually exclusive
            val isBackgroundAndForeground = origin.isBackground && origin.isForeground
            assertFalse(
                "Origin cannot be both background and foreground",
                isBackgroundAndForeground
            )

            // At least one should be true
            val isEitherBackgroundOrForeground = origin.isBackground || origin.isForeground
            assertTrue(
                "Origin must be either background or foreground",
                isEitherBackgroundOrForeground
            )
        }
    }

    @Test
    fun `test toString representation`() {
        val foreground = IdleOrigin.Foreground
        val background = IdleOrigin.Background

        val foregroundString = foreground.toString()
        val backgroundString = background.toString()

        assertTrue(
            "Foreground toString should contain 'Foreground'",
            foregroundString.contains("Foreground", ignoreCase = true)
        )
        assertTrue(
            "Background toString should contain 'Background'",
            backgroundString.contains("Background", ignoreCase = true)
        )
    }
}
