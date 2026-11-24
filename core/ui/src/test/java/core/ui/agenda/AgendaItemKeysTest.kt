package core.ui.agenda

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AgendaItemKeysTest {
    @Test
    fun `keys an all-day event's rows apart on each day it spans`() {
        val inputEvent: AgendaEventModel = createAgendaEventModel(eventId = 1L, title = "Holiday", timeText = null)
        val inputFirstDay: AgendaDayModel = createAgendaDayModel(date = LocalDate.of(2026, 10, 20), events = listOf(inputEvent))
        val inputLastDay: AgendaDayModel = createAgendaDayModel(date = LocalDate.of(2026, 10, 21), events = listOf(inputEvent))

        val actualFirstKey: String = eventItemKey(day = inputFirstDay, event = inputEvent)
        val actualLastKey: String = eventItemKey(day = inputLastDay, event = inputEvent)

        assertNotEquals(actualFirstKey, actualLastKey)
    }

    @Test
    fun `keys recurring instances of one event apart by their begin and end`() {
        val inputFirstInstance: AgendaEventModel = createAgendaEventModel(eventId = 7L, beginMillis = 100L, endMillis = 200L)
        val inputLaterBegin: AgendaEventModel = createAgendaEventModel(eventId = 7L, beginMillis = 300L, endMillis = 200L)
        val inputLaterEnd: AgendaEventModel = createAgendaEventModel(eventId = 7L, beginMillis = 100L, endMillis = 400L)
        val inputDay: AgendaDayModel = createAgendaDayModel(events = listOf(inputFirstInstance, inputLaterBegin, inputLaterEnd))

        val actualFirstKey: String = eventItemKey(day = inputDay, event = inputFirstInstance)

        assertNotEquals(actualFirstKey, eventItemKey(day = inputDay, event = inputLaterBegin))
        assertNotEquals(actualFirstKey, eventItemKey(day = inputDay, event = inputLaterEnd))
    }

    @Test
    fun `keeps an event row and its day heading keyed when neighbouring events change`() {
        val inputEarlierEvent: AgendaEventModel = createAgendaEventModel(eventId = 1L)
        val inputEvent: AgendaEventModel = createAgendaEventModel(eventId = 2L, title = "Review")
        val inputLaterEvent: AgendaEventModel = createAgendaEventModel(eventId = 3L, title = "Release")
        val inputDay: AgendaDayModel = createAgendaDayModel(events = listOf(inputEarlierEvent, inputEvent))
        val expectedEventKey: String = eventItemKey(day = inputDay, event = inputEvent)
        val expectedDayKey: String = dayItemKey(inputDay)
        val actualDayWithoutEarlierEvent: AgendaDayModel = inputDay.copy(events = listOf(inputEvent))
        val actualDayWithLaterEvent: AgendaDayModel = inputDay.copy(events = listOf(inputEvent, inputLaterEvent))

        assertEquals(expectedEventKey, eventItemKey(day = actualDayWithoutEarlierEvent, event = inputEvent))
        assertEquals(expectedEventKey, eventItemKey(day = actualDayWithLaterEvent, event = inputEvent))
        assertEquals(expectedDayKey, dayItemKey(actualDayWithoutEarlierEvent))
        assertEquals(expectedDayKey, dayItemKey(actualDayWithLaterEvent))
    }
}
