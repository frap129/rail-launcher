package core.widgets

import androidx.annotation.StringRes
import core.data.widgets.AppWidgetId
import core.data.widgets.BindRequest
import core.data.widgets.HeaderRemoval
import core.data.widgets.ReplaceHeaderUndo

/** How the Widget Area or a Pop-up undoes a removal. */
sealed interface UndoAction {
    data class RemoveHeader(val removal: HeaderRemoval) : UndoAction

    data class ReplaceHeader(val undo: ReplaceHeaderUndo) : UndoAction

    data class IncludeMediaApp(val packageName: String, val appLabel: String = packageName) : UndoAction
}

/** An event the Widget Area or a Pop-up asks the Activity to run. */
sealed interface WidgetEvent {
    data class ShowUndo(val action: UndoAction) : WidgetEvent

    data object RequestCalendarPermission : WidgetEvent

    data class ShowCalendarPermissionDenied(val permanentlyDenied: Boolean) : WidgetEvent

    data object NavigateToWidgetSettings : WidgetEvent

    data object NavigateToMediaPlayerSettings : WidgetEvent

    data object EditFavorites : WidgetEvent

    data object RequestNotificationAccess : WidgetEvent

    data class LaunchBind(val request: BindRequest) : WidgetEvent

    data class LaunchConfigure(val id: AppWidgetId) : WidgetEvent

    data class LaunchReconfigure(val id: AppWidgetId) : WidgetEvent

    data class RequestQuietModeOff(val profileSerial: Long) : WidgetEvent

    data class ShowMessage(@StringRes val message: Int, val formatArg: String? = null) : WidgetEvent

    data class RemoveHostView(val appWidgetId: AppWidgetId) : WidgetEvent

    data class ReportWidgetSize(val appWidgetIds: List<AppWidgetId>, val widthDp: Int, val heightDp: Int) : WidgetEvent

    data class ScrollToPlace(val placeId: Long) : WidgetEvent

    data object ReturnToSettings : WidgetEvent
}
