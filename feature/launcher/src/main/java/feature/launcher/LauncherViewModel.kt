package feature.launcher

import android.content.Context
import androidx.compose.foundation.lazy.LazyListState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import core.data.launchables.ILaunchableRepository
import core.data.launchables.model.Launchable
import core.data.launchables.model.LaunchableGroup
import core.data.prefs.IPreferencesRepository
import core.data.prefs.IPreferencesRepositoryImpl
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
    data class AppList(val groups: List<LaunchableGroup>, val lazyListState: LazyListState) : LauncherUiState()
    data class Scrolling(val group: LaunchableGroup) : LauncherUiState()
}

sealed class LauncherMenuState {
    data object Closed : LauncherMenuState()
    data class Menu(val item: Launchable) : LauncherMenuState()
    data class Rename(val item: Launchable) : LauncherMenuState()
}

class LauncherViewModel(
    context: Context,
    private val launcherItemRepo: ILaunchableRepository,
    private val prefsRepo: IPreferencesRepository
) : ViewModel() {

    val listStartOffsetPx = screenHeightPx(context) / 5
    val listEndOffsetPx = screenHeightPx(context) - listStartOffsetPx

    val launchableGroups = launcherItemRepo.launchableGroups
    private val scrolling: MutableStateFlow<Boolean> = MutableStateFlow(false)
    private val selectedGroup: MutableStateFlow<Int> = MutableStateFlow(-1)
    val uiState: StateFlow<LauncherUiState> = combine(
        launchableGroups,
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
    private val menuItem: MutableStateFlow<Launchable?> = MutableStateFlow(null)
    val menuState: StateFlow<LauncherMenuState> = menuItem.combine(launcherItemRepo.launchables) { requestedItem, allItems ->
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

    fun openItemMenu(launchable: Launchable) {
        menuItem.value = launchable
        requestedMenuState.value = LauncherMenuState.Menu(launchable)
    }

    fun openItemRename(launchable: Launchable) {
        requestedMenuState.value = LauncherMenuState.Rename(launchable)
    }

    fun closeItemMenu() {
        requestedMenuState.value = LauncherMenuState.Closed
        menuItem.value = null
    }

    fun setItemName(launchable: Launchable, name: String) {
        prefsRepo.setItemName(launchable, name)
        openItemMenu(launchable)
    }
}
