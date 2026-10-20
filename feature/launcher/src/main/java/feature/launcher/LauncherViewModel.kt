package feature.launcher

import androidx.compose.foundation.lazy.LazyListState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import core.data.favorites.IFavoritesRepository
import core.data.launchables.ILaunchableRepository
import core.data.launchables.model.Launchable
import core.data.launchables.model.LaunchableGroup
import core.data.prefs.IPreferencesRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A panel rendered in Home's Widget Area. */
sealed interface Widget

sealed class LauncherUiState {
    data object Error : LauncherUiState()
    data object Loading : LauncherUiState()

    data class Home(val lazyListState: LazyListState, val favorites: List<Launchable>, val widgets: List<Widget> = emptyList()) :
        LauncherUiState()

    sealed class AppList : LauncherUiState() {
        data class Browsing(val groups: List<LaunchableGroup.Listed>, val lazyListState: LazyListState) : AppList()

        data class Scrolling(val group: LaunchableGroup.Listed) : AppList()
    }
}

private enum class LauncherMode {
    Home,
    AppList
}

sealed class LauncherMenuState {
    data object Closed : LauncherMenuState()
    data class Menu(val item: Launchable, val isFavorite: Boolean) : LauncherMenuState()
    data class Rename(val item: Launchable) : LauncherMenuState()
}

private enum class MenuRequest {
    Closed,
    Menu,
    Rename
}

class LauncherViewModel(
    listOffsets: ListOffsets,
    private val launcherItemRepo: ILaunchableRepository,
    private val prefsRepo: IPreferencesRepository,
    private val favoritesRepo: IFavoritesRepository
) : ViewModel() {

    val listStartOffsetPx: Int = listOffsets.startPx
    val listEndOffsetPx: Int = listOffsets.endPx

    val launchableGroups: Flow<List<LaunchableGroup>> = launcherItemRepo.launchableGroups
    private val mode: MutableStateFlow<LauncherMode> = MutableStateFlow(LauncherMode.Home)
    private val scrolling: MutableStateFlow<Boolean> = MutableStateFlow(false)
    private val selectedGroup: MutableStateFlow<LaunchableGroup.Listed?> = MutableStateFlow(null)
    private val homeListState: MutableStateFlow<LazyListState> = MutableStateFlow(LazyListState())
    private val homeState: Flow<LauncherUiState.Home> = combine(homeListState, favoritesRepo.favorites) { listState, favorites ->
        LauncherUiState.Home(lazyListState = listState, favorites = favorites)
    }
    val uiState: StateFlow<LauncherUiState> = combine(
        launchableGroups,
        mode,
        scrolling,
        selectedGroup,
        homeState
    ) { groups, mode, scrolling, selected, home ->
        val listedGroups: List<LaunchableGroup.Listed> = groups.filterIsInstance<LaunchableGroup.Listed>()
        when {
            listedGroups.isEmpty() -> LauncherUiState.Loading

            mode == LauncherMode.Home -> home

            scrolling && selected == null -> LauncherUiState.Loading

            scrolling && selected != null -> LauncherUiState.AppList.Scrolling(selected)

            !scrolling -> LauncherUiState.AppList.Browsing(
                groups = listedGroups,
                lazyListState = LazyListState(
                    firstVisibleItemIndex = listedGroups.indexOfFirst { it == selected } + 1,
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

    private val requestedMenuState: MutableStateFlow<MenuRequest> = MutableStateFlow(MenuRequest.Closed)
    private val menuItem: MutableStateFlow<Launchable?> = MutableStateFlow(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val menuItemIsFavorite: Flow<Boolean> = menuItem.flatMapLatest { item: Launchable? ->
        if (item == null) flowOf(false) else favoritesRepo.isFavorite(item)
    }
    val menuState: StateFlow<LauncherMenuState> = combine(
        menuItem.combine(launcherItemRepo.launchables) { requestedItem, allItems ->
            allItems.find { it.key == requestedItem?.key }
        },
        requestedMenuState,
        menuItemIsFavorite
    ) { item, requestedState, isFavorite ->
        if (item == null) {
            LauncherMenuState.Closed
        } else {
            when (requestedState) {
                MenuRequest.Closed -> LauncherMenuState.Closed
                MenuRequest.Menu -> LauncherMenuState.Menu(item = item, isFavorite = isFavorite)
                MenuRequest.Rename -> LauncherMenuState.Rename(item)
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(),
        initialValue = LauncherMenuState.Closed
    )

    fun onRailTouched(group: LaunchableGroup) {
        preview(group)
    }

    fun onRailMoved(group: LaunchableGroup) {
        preview(group)
    }

    fun onRailReleased(group: LaunchableGroup) {
        when (group) {
            LaunchableGroup.Star -> showHomeFromRail()

            is LaunchableGroup.Listed -> {
                selectedGroup.value = group
                scrolling.value = false
                mode.value = LauncherMode.AppList
            }
        }
    }

    private fun preview(group: LaunchableGroup) {
        when (group) {
            LaunchableGroup.Star -> showHomeFromRail()
            is LaunchableGroup.Listed -> showScrolling(group)
        }
    }

    private fun showHomeFromRail() {
        if (mode.value == LauncherMode.AppList) {
            showHome(resetScroll = false)
        }
    }

    private fun showScrolling(group: LaunchableGroup.Listed) {
        selectedGroup.value = group
        scrolling.value = true
        mode.value = LauncherMode.AppList
    }

    /**
     * Returns whether Back was handled. Back returns from the App List to
     * Home and is not consumed on Home.
     */
    fun onBack(): Boolean {
        if (mode.value == LauncherMode.Home) {
            return false
        }

        showHome(resetScroll = false)
        return true
    }

    /**
     * The launcher was re-selected through the HOME category. Home is shown
     * scrolled to the top.
     */
    fun onHomeIntent() {
        showHome(resetScroll = true)
    }

    /**
     * A Launchable was launched. Home is shown scrolled to the top for the
     * next return.
     */
    fun onLaunchableLaunched() {
        showHome(resetScroll = true)
    }

    private fun showHome(resetScroll: Boolean) {
        mode.value = LauncherMode.Home
        scrolling.value = false
        selectedGroup.value = null
        if (resetScroll) {
            homeListState.value = LazyListState()
        }
    }

    fun openItemMenu(launchable: Launchable) {
        menuItem.value = launchable
        requestedMenuState.value = MenuRequest.Menu
    }

    fun openItemRename(launchable: Launchable) {
        requestedMenuState.value = MenuRequest.Rename
    }

    fun closeItemMenu() {
        requestedMenuState.value = MenuRequest.Closed
        menuItem.value = null
    }

    fun setItemName(launchable: Launchable, name: String) {
        prefsRepo.setItemName(launchable, name)
        openItemMenu(launchable)
    }

    fun addToFavorites(launchable: Launchable) {
        setFavorite(launchable, favoritesRepo::add)
    }

    fun removeFromFavorites(launchable: Launchable) {
        setFavorite(launchable, favoritesRepo::remove)
    }

    private fun setFavorite(launchable: Launchable, updateFavorite: suspend (Launchable) -> Unit) {
        viewModelScope.launch {
            updateFavorite(launchable)
        }
        closeItemMenu()
    }
}
