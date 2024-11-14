package feature.launcher

import core.data.launcher.model.LauncherItem
import kotlinx.coroutines.flow.Flow

enum class BottomSheetStatus {
    CLOSED,
    MENU,
    RENAME
}

class BottomSheetState(val item: Flow<LauncherItem?>)
