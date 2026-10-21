package feature.launcher

import androidx.compose.foundation.lazy.LazyListState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import core.data.favorites.IFavoritesRepository
import core.data.launchables.ILaunchableRepository
import core.data.launchables.LaunchableState
import core.data.launchables.model.Launchable
import core.data.launchables.model.LaunchableGroup
import core.data.prefs.IPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

/** A panel rendered in Home's Widget Area. */
sealed interface Widget

sealed class LauncherUiState {
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

private const val VIEW_MODEL_STATE_TIMEOUT_MS: Long = 5_000

// Favorites missing from [keys] keep their relative order after the ordered ones.
private fun List<Launchable>.sortedInOrderOf(keys: List<String>): List<Launchable> {
    val indexByKey: Map<String, Int> = keys.withIndex().associate { (index, key) -> key to index }
    return sortedBy { indexByKey[it.key] ?: Int.MAX_VALUE }
}

private data class RailNavigation(
    val mode: LauncherMode = LauncherMode.Home,
    val isScrolling: Boolean = false,
    val selected: LaunchableGroup.Listed? = null,
    val homeListState: LazyListState = LazyListState(),
    val appListState: LazyListState? = null
)

private fun toUiState(
    navigation: RailNavigation,
    state: LaunchableState,
    favorites: List<Launchable>,
    isEditingFavorites: Boolean
): LauncherUiState {
    val listedGroups: List<LaunchableGroup.Listed> = state.listedGroups
    if (listedGroups.isEmpty()) {
        return LauncherUiState.Loading
    }
    if (navigation.mode == LauncherMode.Home) {
        return LauncherUiState.Home(
            lazyListState = navigation.homeListState,
            favorites = favorites,
            isEditingFavorites = isEditingFavorites
        )
    }
    val selected: LaunchableGroup.Listed? = navigation.selected
    if (navigation.isScrolling) {
        return if (selected == null) {
            LauncherUiState.Loading
        } else {
            LauncherUiState.AppList.Scrolling(group = selected, isEditingFavorites = isEditingFavorites)
        }
    }
    val appListState: LazyListState = navigation.appListState ?: return LauncherUiState.Loading
    return LauncherUiState.AppList.Browsing(
        groups = listedGroups,
        lazyListState = appListState,
        isEditingFavorites = isEditingFavorites
    )
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

    private val launchableState: StateFlow<LaunchableState> = launcherItemRepo.launchableState

    /** The Rail labels in index order, shared with the Rail. */
    val railLabels: StateFlow<List<String>> = launchableState
        .map { state -> state.groups.map { group -> group.label } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(VIEW_MODEL_STATE_TIMEOUT_MS),
            initialValue = emptyList()
        )

    // Mode, selection, and scroll positions change together, so one Rail event is one emission.
    private val navigation: MutableStateFlow<RailNavigation> = MutableStateFlow(RailNavigation())

    // Keys of the Favorites in their edited order; null outside Edit Favorites mode.
    private val editedFavoriteKeys: MutableStateFlow<List<String>?> = MutableStateFlow(null)

    /** Whether the Favorites are being edited, shared with the Done button. */
    val isEditingFavorites: StateFlow<Boolean> = editedFavoriteKeys
        .map { keys -> keys != null }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = false
        )

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
        started = SharingStarted.WhileSubscribed(VIEW_MODEL_STATE_TIMEOUT_MS),
        initialValue = emptyList()
    )

    val uiState: StateFlow<LauncherUiState> = combine(
        navigation,
        launchableState,
        favorites,
        isEditingFavorites
    ) { navigation, state, favorites, isEditingFavorites ->
        toUiState(
            navigation = navigation,
            state = state,
            favorites = favorites,
            isEditingFavorites = isEditingFavorites
        )
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

    fun onRailPreview(index: Int) = handleRailGroup(index, isScrolling = true)

    fun onRailReleased(index: Int) = handleRailGroup(index, isScrolling = false)

    private fun handleRailGroup(index: Int, isScrolling: Boolean) {
        when (val group = findRailGroup(index) ?: return) {
            LaunchableGroup.Star -> showHomeFromRail()

            is LaunchableGroup.Listed -> {
                val appListState: LazyListState? = if (isScrolling) null else createAppListState(group)
                navigation.update { current ->
                    toAppListNavigation(
                        current = current,
                        group = group,
                        isScrolling = isScrolling,
                        appListState = appListState ?: current.appListState
                    )
                }
            }
        }
    }

    // Reads the shared state before update so a retried update cannot build a second scroll state.
    private fun createAppListState(group: LaunchableGroup.Listed): LazyListState {
        val listedGroups: List<LaunchableGroup.Listed> = launchableState.value.listedGroups
        return LazyListState(
            firstVisibleItemIndex = listedGroups.indexOfFirst { listed -> listed == group } + 1,
            firstVisibleItemScrollOffset = -listStartOffsetPx
        )
    }

    private fun toAppListNavigation(
        current: RailNavigation,
        group: LaunchableGroup.Listed,
        isScrolling: Boolean,
        appListState: LazyListState?
    ): RailNavigation = current.copy(
        mode = LauncherMode.AppList,
        isScrolling = isScrolling,
        selected = group,
        appListState = appListState
    )

    private fun findRailGroup(index: Int): LaunchableGroup? {
        val groups: List<LaunchableGroup> = launchableState.value.groups
        val group: LaunchableGroup? = groups.getOrNull(index)
        if (group == null) {
            Timber.w("Ignoring Rail index %d outside %d groups", index, groups.size)
        }
        return group
    }

    private fun showHomeFromRail() {
        if (navigation.value.mode == LauncherMode.AppList) {
            showHome(resetScroll = false)
        }
    }

    /**
     * Back from the App List returns to Home with Edit Favorites intact. Back on
     * Home leaves Edit Favorites, keeping the pending order, and is a no-op
     * outside Edit Favorites. Home always consumes Back so the launcher activity
     * is never finished.
     */
    fun onBack() {
        if (navigation.value.mode == LauncherMode.AppList) {
            showHome(resetScroll = false)
        } else {
            finishEditingFavorites()
        }
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
        navigation.update { current ->
            current.copy(
                mode = LauncherMode.Home,
                isScrolling = false,
                selected = null,
                appListState = null,
                homeListState = if (resetScroll) LazyListState() else current.homeListState
            )
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
