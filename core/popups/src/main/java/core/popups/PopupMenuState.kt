package core.popups

import core.data.apps.model.App
import core.data.launchables.model.Launchable
import core.data.shortcuts.ShortcutLaunchable
import core.data.widgets.WidgetProvider

/** One action in a Pop-up menu or an added item's menu. */
sealed interface PopupMenuItem {
    data object EditPopup : PopupMenuItem

    data object EditFolder : PopupMenuItem

    data object DeleteFolder : PopupMenuItem

    data object AddWidget : PopupMenuItem

    data object AddToStack : PopupMenuItem

    data object ResizeWidget : PopupMenuItem

    data object ReconfigureWidget : PopupMenuItem

    data object RemoveWidget : PopupMenuItem

    data object RemoveFromStack : PopupMenuItem

    data object RemoveFromPopup : PopupMenuItem

    data object AppInfo : PopupMenuItem
}

/** Whether a Pop-up menu is showing and, when it is, its target and actions. */
sealed interface PopupMenuState {
    data object Closed : PopupMenuState

    data class Open(
        val target: Launchable,
        val items: List<PopupMenuItem>,
        /** The Stack page whose menu this is; null unless the menu belongs to a widget page. */
        val widgetPage: PopupWidgetPage? = null
    ) : PopupMenuState
}

/** What a menu's App info action opens. */
sealed interface PopupAppInfo {
    /** The App info of [launchable], an App or the source App of a Shortcut item. */
    data class LaunchableAppInfo(val launchable: Launchable) : PopupAppInfo

    /** The App info of the App that provides a widget menu's Stack page. */
    data class WidgetProviderAppInfo(val provider: WidgetProvider, val profileSerial: Long) : PopupAppInfo
}

/**
 * The App info a menu over [target] offers, or null when it offers none.
 * A widget menu's [widgetPage] supplies its provider; a Folder owner has none.
 */
internal fun appInfoFor(target: Launchable, widgetPage: PopupWidgetPage?): PopupAppInfo? = when {
    widgetPage != null && target is App -> PopupAppInfo.WidgetProviderAppInfo(
        provider = widgetPage.provider,
        profileSerial = widgetPage.profileSerial
    )

    target is App || target is ShortcutLaunchable -> PopupAppInfo.LaunchableAppInfo(launchable = target)

    else -> null
}
