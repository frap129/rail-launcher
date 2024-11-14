package feature.launcher

import android.content.Context
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import core.data.launcher.LauncherItemRepository
import core.data.launcher.model.LauncherItem
import core.data.launcher.model.LauncherItemGroup
import core.data.prefs.PreferencesRepository
import core.util.screenHeightPx
import kotlin.math.max
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

sealed class LauncherUiState {
    data object Error : LauncherUiState()
    data object Loading : LauncherUiState()
    data class AppList(val groups: List<LauncherItemGroup>, val lazyListState: LazyListState) : LauncherUiState()
    data class Scrolling(val group: LauncherItemGroup) : LauncherUiState()
}

class LauncherViewModel(
    context: Context,
    private val launcherItemRepo: LauncherItemRepository,
    private val prefsRepo: PreferencesRepository
) : ViewModel() {

    val endScrollOffset = screenHeightPx(context) / 5 + 8

    val launcherItemGroups = launcherItemRepo.launcherItemGroups
    private val scrolling: MutableStateFlow<Boolean> = MutableStateFlow(false)
    private val selectedGroup: MutableStateFlow<Int> = MutableStateFlow(-1)

    val uiState: StateFlow<LauncherUiState> = combine(
        launcherItemGroups,
        scrolling,
        selectedGroup
    ) { groups, scrolling, selected ->
        when {
            groups.isEmpty() -> LauncherUiState.Loading

            scrolling && selected < 0 -> LauncherUiState.Loading

            scrolling -> LauncherUiState.Scrolling(groups[selected])

            !scrolling -> LauncherUiState.AppList(
                groups = groups,
                lazyListState = LazyListState(
                    firstVisibleItemIndex = max(selected + 1, 0),
                    firstVisibleItemScrollOffset = -endScrollOffset

                )
            )

            else -> LauncherUiState.Error
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LauncherUiState.Loading
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val bottomSheetState: MutableState<BottomSheetStatus> = mutableStateOf(BottomSheetStatus.CLOSED)
    val bottomSheetItem: MutableState<Flow<LauncherItem?>?> = mutableStateOf(null)

    fun onScrollStarted(index: Int) {
        selectedGroup.value = index
        scrolling.value = true
    }

    fun onScroll(index: Int) {
        selectedGroup.value = index
    }

    fun onScrollEnded(index: Int) {
        scrolling.value = false
    }

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
