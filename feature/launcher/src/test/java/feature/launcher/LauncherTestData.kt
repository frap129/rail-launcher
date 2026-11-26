package feature.launcher

import core.data.launchables.model.Launchable
import core.data.profiles.ProfileInfo
import core.ui.composables.MenuSuggestion

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
