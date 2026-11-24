package core.ui.agenda

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import core.ui.calendar.OpenEventTarget
import java.time.LocalDate

/** One day of the agenda: its date, its heading and its events, ready to draw. */
@Immutable
data class AgendaDayModel(val date: LocalDate, val label: String, val events: List<AgendaEventModel>)

/** One event row of the agenda. */
@Immutable
data class AgendaEventModel(val title: String, val timeText: String?, val color: Color, val tapTarget: OpenEventTarget)
