package core.data.launcher

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf

@Immutable
abstract class LauncherItem {
    abstract val key: String
    abstract val defaultName: String
    abstract fun getDefaultIcon(context: Context): Drawable
    abstract fun launch(context: Context)

    private val hasCustomIcon: Boolean = false

    val name: MutableState<String> = mutableStateOf(defaultName)

    fun getIcon(context: Context): Drawable = if (hasCustomIcon) {
        // TODO: Implement Custom Icons
        getDefaultIcon(context)
    } else {
        getDefaultIcon(context)
    }

    fun setIcon(uri: String) {
        // TODO: Implement Custom Icons
    }
}
