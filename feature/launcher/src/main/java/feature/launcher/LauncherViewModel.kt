package feature.launcher

import androidx.compose.foundation.lazy.LazyListState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import core.data.launchables.ILaunchableRepository
import core.data.launchables.model.Launchable
import core.data.launchables.model.LaunchableGroup
import core.data.prefs.IPreferencesRepository
import kotlin.math.max
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

sealed class LauncherUiState {
    data object Error : LauncherUiState()
    data object Loading : LauncherUiState()

    sealed class AppList : LauncherUiState() {
        data class List(val groups: kotlin.collections.List<LaunchableGroup>, val lazyListState: LazyListState) : AppList()

        data class Scrolling(val group: LaunchableGroup) : AppList()
    }
}

sealed class LauncherMenuState {
    data object Closed : LauncherMenuState()
    data class Menu(val item: Launchable) : LauncherMenuState()
    data class Rename(val item: Launchable) : LauncherMenuState()
}

class LauncherViewModel(
    listOffsets: ListOffsets,
    private val launcherItemRepo: ILaunchableRepository,
    private val prefsRepo: IPreferencesRepository
) : ViewModel() {

    val listStartOffsetPx: Int = listOffsets.startPx
    val listEndOffsetPx: Int = listOffsets.endPx

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

            scrolling -> LauncherUiState.AppList.Scrolling(groups[selected])

            !scrolling -> LauncherUiState.AppList.List(
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

    fun onRailTouched(index: Int) {
        selectedGroup.value = index
        scrolling.value = true
    }

    fun onRailMoved(index: Int) {
        selectedGroup.value = index
    }

    fun onRailReleased(index: Int) {
        selectedGroup.value = index
        scrolling.value = false
    }

    /**
     * Returns whether Back was handled. The App List is the root mode until
     * the Home mode is added in rail-nf7.4, so Back is not consumed yet.
     */
    fun onBack(): Boolean = false

    /**
     * The launcher was re-selected through the HOME category. Home mode
     * arrives in rail-nf7.4, so the App List is unchanged for now.
     */
    fun onHomeIntent() = Unit

    /**
     * A Launchable was launched. Resetting to Home arrives in rail-nf7.4, so
     * the App List is unchanged for now.
     */
    fun onLaunchableLaunched() = Unit

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
