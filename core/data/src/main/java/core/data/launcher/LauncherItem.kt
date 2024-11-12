package core.data.launcher

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.compose.runtime.Immutable

@Immutable
abstract class LauncherItem {
    abstract val key: String
    abstract fun getDefaultName(): String
    abstract fun getDefaultIcon(context: Context): Drawable
    abstract fun launch(context: Context)

    private val hasCustomName: Boolean = false
    private val hasCustomIcon: Boolean = false

    var name: String
        get() = if (hasCustomName) {
            // TODO: Implement Custom Names
            getDefaultName()
        } else {
            getDefaultName()
        }
        set(value) {
            // TODO: Implement Custom Icons
        }

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
