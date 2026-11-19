package feature.launcher

import core.data.launchables.model.Launchable
import core.data.prefs.RailSide
import core.data.profiles.ProfileInfo

fun createLauncherItemActions(railSide: RailSide = RailSide.DEFAULT): LauncherItemActions = LauncherItemActions(
    onItemTap = { _, _ -> },
    onItemLongPress = {},
    onToggleFavorite = {},
    onItemOpenPopup = { _, _ -> },
    onItemAnchorChanged = { _, _ -> },
    privateSpaceActions = PrivateSpaceActions(onUnlock = {}, onLock = {}, onSettings = null),
    railBounds = { null },
    railSide = railSide
)

/** The menu [LauncherViewModel] resolves for [item], defaulting every action it has not been given to hidden. */
fun createItemMenu(
    item: Launchable,
    suggestion: MenuSuggestion = MenuSuggestion.None,
    isStandaloneShortcut: Boolean = false,
    canUninstall: Boolean = false,
    isHidden: Boolean = false,
    installInPrivateSpaceProfile: ProfileInfo? = null
): LauncherMenuState.Menu = LauncherMenuState.Menu(
    item = item,
    suggestion = suggestion,
    isStandaloneShortcut = isStandaloneShortcut,
    canUninstall = canUninstall,
    isHidden = isHidden,
    installInPrivateSpaceProfile = installInPrivateSpaceProfile
)
