package feature.launcher

import core.data.prefs.RailSide

fun createLauncherItemActions(): LauncherItemActions = LauncherItemActions(
    onItemTap = { _, _ -> },
    onItemLongPress = {},
    onToggleFavorite = {},
    onItemOpenPopup = { _, _ -> },
    onItemAnchorChanged = { _, _ -> },
    privateSpaceActions = PrivateSpaceActions(onUnlock = {}, onLock = {}, onSettings = null),
    railBounds = { null },
    railSide = RailSide.DEFAULT
)
