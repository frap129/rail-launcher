package feature.launcher

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
import core.data.launcher.LauncherItemRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.mapLatest

class LauncherViewModel(private val launcherItemRepo: LauncherItemRepository) : ViewModel() {
    @OptIn(ExperimentalCoroutinesApi::class)
    val launcherItems = launcherItemRepo.launcherItems.mapLatest { items ->
        visibleGroups.addAll(items.keys)
        items
    }

    val scrolling: MutableState<Boolean> = mutableStateOf(false)
    val visibleGroups: SnapshotStateList<Char> = mutableStateListOf()
}
