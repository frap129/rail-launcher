package feature.launcher

import androidx.compose.foundation.lazy.LazyListState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import core.data.favorites.IFavoritesRepository
import core.data.launchables.ILaunchableRepository
import core.data.launchables.SHARED_SUBSCRIPTION_TIMEOUT_MS
import core.data.launchables.model.Launchable
import core.data.launchables.model.LaunchableGroup
import core.data.prefs.IPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

/** A panel rendered in Home's Widget Area. */
sealed interface Widget

sealed class LauncherUiState {
    data object Error : LauncherUiState()
    data object Loading : LauncherUiState()

    data class Home(
        val lazyListState: LazyListState,
        val favorites: List<Launchable>,
        val widgets: List<Widget> = emptyList(),
        val isEditingFavorites: Boolean = false
    ) : LauncherUiState()

    sealed class AppList : LauncherUiState() {
        abstract val isEditingFavorites: Boolean

        data class Browsing(
            val groups: List<LaunchableGroup.Listed>,
            val lazyListState: LazyListState,
            override val isEditingFavorites: Boolean
        ) : AppList()

        data class Scrolling(val group: LaunchableGroup.Listed, override val isEditingFavorites: Boolean) : AppList()
    }
}

private enum class LauncherMode {
    Home,
    AppList
}

sealed class LauncherMenuState {
    data object Closed : LauncherMenuState()
    data class Menu(val item: Launchable) : LauncherMenuState()
    data class Rename(val item: Launchable) : LauncherMenuState()
}

// Favorites missing from [keys] keep their relative order after the ordered ones.
private fun List<Launchable>.sortedInOrderOf(keys: List<String>): List<Launchable> {
    val indexByKey: Map<String, Int> = keys.withIndex().associate { (index, key) -> key to index }
    return sortedBy { indexByKey[it.key] ?: Int.MAX_VALUE }
}

private data class RailContent(val groups: List<LaunchableGroup>, val selected: LaunchableGroup.Listed?)

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

    // Kept across emissions so starring a Launchable does not reset the App List scroll.
    private val appListState: MutableStateFlow<LazyListState?> = MutableStateFlow(null)

    // Keys of the Favorites in their edited order; null outside Edit Favorites mode.
    private val editedFavoriteKeys: MutableStateFlow<List<String>?> = MutableStateFlow(null)

    // Serializes star toggles and Done so a rapid second tap cannot read a stale favorite state.
    private val favoritesMutex: Mutex = Mutex()

    /** The Favorites in their shown order, including the unsaved order during Edit Favorites. */
    val favorites: StateFlow<List<Launchable>> = combine(
        favoritesRepo.favorites,
        editedFavoriteKeys
    ) { favorites, editedKeys ->
        if (editedKeys == null) favorites else favorites.sortedInOrderOf(editedKeys)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SHARED_SUBSCRIPTION_TIMEOUT_MS),
        initialValue = emptyList()
    )

    private val homeState: Flow<LauncherUiState.Home> = combine(
        homeListState,
        favorites,
        editedFavoriteKeys
    ) { listState, favorites, editedKeys ->
        LauncherUiState.Home(
            lazyListState = listState,
            favorites = favorites,
            isEditingFavorites = editedKeys != null
        )
    }
    private val railState: Flow<RailContent> = combine(
        launchableGroups,
        selectedGroup
    ) { groups, selected ->
        RailContent(groups = groups, selected = selected)
    }
    val uiState: StateFlow<LauncherUiState> = combine(
        railState,
        mode,
        scrolling,
        homeState,
        editedFavoriteKeys
    ) { rail, mode, scrolling, home, editedKeys ->
        val listedGroups: List<LaunchableGroup.Listed> = rail.groups.filterIsInstance<LaunchableGroup.Listed>()
        val isEditingFavorites: Boolean = editedKeys != null
        when {
            listedGroups.isEmpty() -> LauncherUiState.Loading

            mode == LauncherMode.Home -> home

            scrolling && rail.selected == null -> LauncherUiState.Loading

            scrolling && rail.selected != null -> LauncherUiState.AppList.Scrolling(rail.selected, isEditingFavorites)

            !scrolling -> LauncherUiState.AppList.Browsing(
                groups = listedGroups,
                lazyListState = appListState.value ?: LazyListState(
                    firstVisibleItemIndex = listedGroups.indexOfFirst { it == rail.selected } + 1,
                    firstVisibleItemScrollOffset = -listStartOffsetPx
                ).also { state -> appListState.value = state },
                isEditingFavorites = isEditingFavorites
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

    val menuState: StateFlow<LauncherMenuState> = combine(
        menuItem.combine(launcherItemRepo.launchables) { requestedItem, allItems ->
            allItems.find { it.key == requestedItem?.key }
        },
        requestedMenuState
    ) { item, requestedState ->
        if (item == null) {
            LauncherMenuState.Closed
        } else {
            when (requestedState) {
                MenuRequest.Closed -> LauncherMenuState.Closed
                MenuRequest.Menu -> LauncherMenuState.Menu(item)
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
                appListState.value = null
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
        appListState.value = null
        if (resetScroll) {
            homeListState.value = LazyListState()
        }
    }

    fun openItemMenu(launchable: Launchable) {
        if (editedFavoriteKeys.value != null) {
            return
        }
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

    fun toggleFavorite(launchable: Launchable) {
        viewModelScope.launch {
            favoritesMutex.withLock {
                if (favoritesRepo.isFavorite(launchable).first()) {
                    favoritesRepo.remove(launchable)
                    editedFavoriteKeys.update { keys -> keys?.filterNot { it == launchable.key } }
                } else {
                    favoritesRepo.add(launchable)
                    editedFavoriteKeys.update { keys -> keys?.plus(launchable.key) }
                }
            }
        }
    }

    fun editFavorites() {
        editedFavoriteKeys.value = favorites.value.map { it.key }
        closeItemMenu()
    }

    fun moveFavorite(fromIndex: Int, toIndex: Int) {
        if (editedFavoriteKeys.value == null) {
            return
        }
        val shownFavorites: List<Launchable> = favorites.value
        if (fromIndex !in shownFavorites.indices || toIndex !in shownFavorites.indices) {
            Timber.w("Ignoring Favorite move from %d to %d outside %d Favorites", fromIndex, toIndex, shownFavorites.size)
            return
        }
        editedFavoriteKeys.value = shownFavorites.map { it.key }.toMutableList().apply {
            add(toIndex, removeAt(fromIndex))
        }
    }

    // Edit mode ends after the order is stored so Home never shows the old order.
    fun finishEditingFavorites() {
        if (editedFavoriteKeys.value == null) {
            return
        }
        viewModelScope.launch {
            favoritesMutex.withLock {
                val keys: List<String> = editedFavoriteKeys.value ?: return@withLock
                favoritesRepo.reorder(favoritesRepo.favorites.first().sortedInOrderOf(keys))
                editedFavoriteKeys.value = null
            }
        }
    }
}
