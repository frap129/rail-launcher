package feature.launcher

import android.content.Context
import androidx.compose.foundation.lazy.LazyListState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import core.data.launcher.LauncherItemRepository
import core.data.launcher.model.LauncherItem
import core.data.launcher.model.LauncherItemGroup
import core.data.prefs.PreferencesRepository
import core.util.screenHeightPx
import kotlin.math.max
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

sealed class LauncherMenuState {
    data object Closed : LauncherMenuState()
    data class Menu(val item: LauncherItem) : LauncherMenuState()
    data class Rename(val item: LauncherItem) : LauncherMenuState()
}

class LauncherViewModel(
    context: Context,
    private val launcherItemRepo: LauncherItemRepository,
    private val prefsRepo: PreferencesRepository
) : ViewModel() {

    val listStartOffsetPx = screenHeightPx(context) / 5
    val listEndOffsetPx = screenHeightPx(context) - listStartOffsetPx

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
                    firstVisibleItemScrollOffset = -listStartOffsetPx

                )
            )

            else -> LauncherUiState.Error
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(),
        initialValue = LauncherUiState.Loading
    )

    private val requestedMenuState: MutableStateFlow<LauncherMenuState> = MutableStateFlow(LauncherMenuState.Closed)
    private val menuItem: MutableStateFlow<LauncherItem?> = MutableStateFlow(null)
    val menuState: StateFlow<LauncherMenuState> = menuItem.combine(launcherItemRepo.launcherItems) { requestedItem, allItems ->
        allItems.find { it.key == requestedItem?.key }
    }.combine(requestedMenuState) { item, requestedState ->
        if (item == null) {
            LauncherMenuState.Closed
        } else {
            when (requestedState) {
                is LauncherMenuState.Closed -> LauncherMenuState.Closed
                is LauncherMenuState.Menu -> LauncherMenuState.Menu(item)
                is LauncherMenuState.Rename -> LauncherMenuState.Rename(item)
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(),
        initialValue = LauncherMenuState.Closed
    )

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
        menuItem.value = launcherItem
        requestedMenuState.value = LauncherMenuState.Menu(launcherItem)
    }

    fun openItemRename(launcherItem: LauncherItem) {
        requestedMenuState.value = LauncherMenuState.Rename(launcherItem)
    }

    fun closeItemMenu() {
        requestedMenuState.value = LauncherMenuState.Closed
        menuItem.value = null
    }

    fun setItemName(launcherItem: LauncherItem, name: String) {
        prefsRepo.setItemName(launcherItem, name)
        openItemMenu(launcherItem)
    }
}
