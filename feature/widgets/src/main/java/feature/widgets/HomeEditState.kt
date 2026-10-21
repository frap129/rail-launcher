package feature.widgets

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class HomeEditMode {
    None,
    Favorites
}

/** The one active Home edit mode, shared with the Widget Area and the launcher. */
class HomeEditState {
    private val currentMode: MutableStateFlow<HomeEditMode> = MutableStateFlow(HomeEditMode.None)

    val mode: StateFlow<HomeEditMode> = currentMode.asStateFlow()

    fun tryEnter(mode: HomeEditMode): Boolean = mode != HomeEditMode.None && currentMode.compareAndSet(HomeEditMode.None, mode)

    fun exit(mode: HomeEditMode) {
        currentMode.compareAndSet(mode, HomeEditMode.None)
    }
}
