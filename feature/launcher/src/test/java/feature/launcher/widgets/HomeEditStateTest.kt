package feature.launcher.widgets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeEditStateTest {
    private val modes: List<HomeEditMode> = listOf(
        HomeEditMode.Favorites,
        HomeEditMode.Resizing(placeId = 7L),
        HomeEditMode.Widgets
    )

    @Test
    fun `starts with no edit mode`() {
        assertEquals(HomeEditMode.None, HomeEditState().mode.value)
    }

    @Test
    fun `enters each mode from None`() {
        modes.forEach { mode ->
            val state: HomeEditState = HomeEditState()
            assertTrue(state.tryEnter(mode))
            assertEquals(mode, state.mode.value)
        }
    }

    @Test
    fun `rejects entering None`() {
        val state: HomeEditState = HomeEditState()
        assertFalse(state.tryEnter(HomeEditMode.None))
        assertEquals(HomeEditMode.None, state.mode.value)
    }

    @Test
    fun `rejects every mode while another mode is active`() {
        modes.forEach { active ->
            modes.forEach { requested ->
                val state: HomeEditState = HomeEditState()
                assertTrue(state.tryEnter(active))
                assertFalse(state.tryEnter(requested))
                assertEquals(active, state.mode.value)
            }
        }
    }

    @Test
    fun `exits only the active mode`() {
        modes.forEach { active ->
            modes.forEach { requestedExit ->
                val state: HomeEditState = HomeEditState()
                assertTrue(state.tryEnter(active))
                state.exit(requestedExit)
                val expected: HomeEditMode = if (active == requestedExit) HomeEditMode.None else active
                assertEquals(expected, state.mode.value)
            }
        }
    }

    @Test
    fun `can enter another mode after exiting`() {
        val state: HomeEditState = HomeEditState()
        assertTrue(state.tryEnter(HomeEditMode.Widgets))
        state.exit(HomeEditMode.Widgets)
        assertTrue(state.tryEnter(HomeEditMode.Favorites))
        assertEquals(HomeEditMode.Favorites, state.mode.value)
    }
}
