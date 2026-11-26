package core.ui.widgets

import androidx.annotation.StringRes
import core.data.icons.model.Icon
import core.data.prefs.ClockStyle
import core.data.widgets.AppWidgetId

/** The long-press menu currently shown on Home. */
sealed interface WidgetMenuState {
    data object Closed : WidgetMenuState

    data class HeaderMenu(val items: List<WidgetMenuItem>) : WidgetMenuState

    data class EmptyAreaMenu(val items: List<WidgetMenuItem>) : WidgetMenuState

    data class AppWidgetMenu(
        val widgetId: Long,
        val appWidgetId: AppWidgetId,
        val icon: Icon,
        val label: String,
        val items: List<WidgetMenuItem>
    ) : WidgetMenuState

    data class MediaPlayerMenu(val icon: Icon, val label: String, val items: List<WidgetMenuItem>) : WidgetMenuState

    data class ClockStyleChooser(val selected: ClockStyle, val previews: List<HeaderModel>) : WidgetMenuState
}

sealed interface WidgetMenuItem {
    val id: WidgetMenuAction

    data class Toggle(
        override val id: WidgetMenuAction,
        val checked: Boolean,
        val enabled: Boolean,
        val supportingText: Int? = null,
        val rowOpensAction: Boolean = false
    ) : WidgetMenuItem

    data class Action(override val id: WidgetMenuAction, val labelArg: String? = null) : WidgetMenuItem

    data class Choice(
        override val id: WidgetMenuAction,
        @StringRes val valueLabel: Int,
        val options: List<WidgetMenuChoiceOption>,
        val selectedKey: WidgetMenuChoiceKey,
        val expanded: Boolean
    ) : WidgetMenuItem
}

@JvmInline
value class WidgetMenuChoiceKey(val value: String)

data class WidgetMenuChoiceOption(val key: WidgetMenuChoiceKey, @StringRes val label: Int, val enabled: Boolean = true)

enum class WidgetMenuAction {
    EditFavorites,
    Clock,
    Date,
    Battery,
    UpcomingEvents,
    ClockStyle,
    AddWidget,
    MoveWidgetArea,
    ReplaceHeader,
    ExtendUnderRail,
    Resize,
    Reconfigure,
    RemoveHeader,
    RestoreHeader,
    RemoveWidget,
    RemoveFromStack,
    EditWidgets,
    WidgetSettings,
    MediaPlayer,
    OpenMediaPlayer,
    ExcludeMediaApp,
    MediaPlayerSettings
}
