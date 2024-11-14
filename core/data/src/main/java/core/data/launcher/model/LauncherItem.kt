package core.data.launcher.model

import android.content.Context
import android.graphics.drawable.Drawable

abstract class LauncherItem {
    abstract val key: String
    abstract val defaultName: String
    abstract fun getDefaultIcon(context: Context): Drawable
    abstract fun launch(context: Context)

    private val hasCustomIcon: Boolean = false

    var name: String = defaultName

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
