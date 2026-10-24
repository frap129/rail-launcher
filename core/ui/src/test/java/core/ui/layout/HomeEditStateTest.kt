package core.ui.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeEditStateTest {
    private val inputModes: List<HomeEditMode> = listOf(
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
        inputModes.forEach { mode ->
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
        inputModes.forEach { active ->
            inputModes.forEach { requested ->
                val state: HomeEditState = HomeEditState()
                assertTrue(state.tryEnter(active))
                assertFalse(state.tryEnter(requested))
                assertEquals(active, state.mode.value)
            }
        }
    }

    @Test
    fun `exits only the active mode`() {
        inputModes.forEach { active ->
            inputModes.forEach { requestedExit ->
                val state: HomeEditState = HomeEditState()
                assertTrue(state.tryEnter(active))
                state.exit(requestedExit)
                val expectedMode: HomeEditMode = if (active == requestedExit) HomeEditMode.None else active
                assertEquals(expectedMode, state.mode.value)
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
