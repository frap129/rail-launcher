package core.ui.agenda

import androidx.compose.ui.graphics.Color
import core.ui.calendar.OpenEventTarget
import java.time.LocalDate

internal fun createAgendaEventModel(
    eventId: Long = 1L,
    title: String = "Standup",
    timeText: String? = "15:00",
    beginMillis: Long = 0L,
    endMillis: Long = 0L
): AgendaEventModel = AgendaEventModel(
    title = title,
    timeText = timeText,
    color = Color(0xFF00FF00.toInt()),
    tapTarget = OpenEventTarget(
        eventId = eventId,
        beginMillis = beginMillis,
        endMillis = endMillis,
        isWorkProfile = false
    )
)

internal fun createAgendaDayModel(
    date: LocalDate = LocalDate.of(2026, 10, 20),
    label: String = "Today",
    events: List<AgendaEventModel> = emptyList()
): AgendaDayModel = AgendaDayModel(date = date, label = label, events = events)
