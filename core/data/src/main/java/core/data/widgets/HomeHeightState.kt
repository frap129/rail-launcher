package core.data.widgets

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The last safe Home height reported by the launcher, shared with Settings. */
class HomeHeightState {
    private val current: MutableStateFlow<Int?> = MutableStateFlow(null)

    val heightDp: StateFlow<Int?> = current.asStateFlow()

    fun setHeight(heightDp: Int) {
        if (heightDp > 0) {
            current.value = heightDp
        }
    }
}
