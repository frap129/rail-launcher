package core.ui.agenda

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import core.ui.R
import core.ui.calendar.OpenEventTarget
import core.ui.composables.EventDot
import core.ui.composables.LaunchableSectionHeader
import core.ui.composables.SheetDragHandle
import core.ui.composables.SheetRow
import core.ui.composables.sheetHorizontalPadding
import core.ui.composables.sheetRowContentInset
import core.ui.composables.sheetVerticalPadding
import core.ui.popups.PopupSheetTitleAction
import core.ui.popups.PopupSheetTitleRow

private const val DAY_KEY_PREFIX: String = "agenda-day-"
private const val EVENT_KEY_PREFIX: String = "agenda-event-"

/**
 * The agenda sheet listing upcoming events by local day. Back and a swipe down dismiss it through
 * [onDismiss]; the title's Open calendar action opens the calendar App.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgendaSheet(
    days: List<AgendaDayModel>,
    isLoading: Boolean,
    onOpenEvent: (OpenEventTarget) -> Unit,
    onOpenCalendar: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { SheetDragHandle() }
    ) {
        PopupSheetTitleRow(
            title = stringResource(R.string.agenda),
            action = PopupSheetTitleAction(label = stringResource(R.string.open_calendar), onClick = onOpenCalendar)
        )
        when {
            isLoading -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = sheetVerticalPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }

            days.isEmpty() -> Text(
                text = stringResource(R.string.agenda_empty),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = sheetRowContentInset, vertical = sheetVerticalPadding),
                style = MaterialTheme.typography.bodyLarge
            )

            else -> LazyColumn(modifier = Modifier.fillMaxWidth()) {
                days.forEachIndexed { dayIndex, day ->
                    item(key = "$DAY_KEY_PREFIX$dayIndex") {
                        LaunchableSectionHeader(title = day.label, endPadding = sheetHorizontalPadding)
                    }
                    itemsIndexed(items = day.events, key = { eventIndex, _ -> "$EVENT_KEY_PREFIX$dayIndex-$eventIndex" }) { _, event ->
                        AgendaEventRow(event = event, onOpen = { onOpenEvent(event.tapTarget) })
                    }
                }
            }
        }
    }
}

@Composable
private fun AgendaEventRow(event: AgendaEventModel, onOpen: () -> Unit) {
    val openLabel: String = stringResource(R.string.open_event, event.title)
    SheetRow(
        modifier = Modifier.clickable(role = Role.Button, onClickLabel = openLabel, onClick = onOpen),
        headlineContent = { Text(text = event.title) },
        supportingContent = event.timeText?.let { timeText ->
            { Text(text = timeText) }
        },
        leadingContent = {
            EventDot(color = event.color)
        }
    )
}
