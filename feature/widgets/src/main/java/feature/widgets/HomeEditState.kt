package feature.widgets

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The one active Home edit mode, shared with the Widget Area and the launcher. */
sealed interface HomeEditMode {
    data object None : HomeEditMode

    data object Favorites : HomeEditMode

    data class Resizing(val placeId: Long) : HomeEditMode
}

/** Holds the one active Home edit mode. */
class HomeEditState {
    private val currentMode: MutableStateFlow<HomeEditMode> = MutableStateFlow(HomeEditMode.None)

    val mode: StateFlow<HomeEditMode> = currentMode.asStateFlow()

    fun tryEnter(mode: HomeEditMode): Boolean = mode != HomeEditMode.None && currentMode.compareAndSet(HomeEditMode.None, mode)

    fun exit(mode: HomeEditMode) {
        currentMode.compareAndSet(mode, HomeEditMode.None)
    }
}
