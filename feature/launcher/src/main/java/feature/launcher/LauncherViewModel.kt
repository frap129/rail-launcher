package feature.launcher

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
import core.data.launcher.LauncherItem
import core.data.launcher.LauncherItemRepository
import core.data.prefs.PreferencesRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapLatest

class LauncherViewModel(private val launcherItemRepo: LauncherItemRepository, private val prefsRepo: PreferencesRepository) : ViewModel() {
    @OptIn(ExperimentalCoroutinesApi::class)
    val launcherItems = launcherItemRepo.launcherItems.mapLatest { items ->
        visibleGroups.addAll(items.keys)
        items
    }

    val scrolling: MutableState<Boolean> = mutableStateOf(false)
    val visibleGroups: SnapshotStateList<Char> = mutableStateListOf()
    val bottomSheetState: MutableState<BottomSheetStatus> = mutableStateOf(BottomSheetStatus.CLOSED)
    val bottomSheetItem: MutableState<Flow<LauncherItem?>?> = mutableStateOf(null)

    fun openItemMenu(launcherItem: LauncherItem) {
        bottomSheetItem.value = launcherItemRepo.getItemByKey(launcherItem.key)
        bottomSheetState.value = BottomSheetStatus.MENU
    }

    fun openItemRename() {
        bottomSheetState.value = BottomSheetStatus.RENAME
    }

    fun closeItemMenu() {
        bottomSheetState.value = BottomSheetStatus.CLOSED
        bottomSheetItem.value = null
    }

    fun setItemName(launcherItem: LauncherItem, name: String) = prefsRepo.setItemName(launcherItem, name)
}
