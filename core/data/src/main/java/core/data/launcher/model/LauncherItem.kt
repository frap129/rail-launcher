package core.data.launcher.model

import android.content.Context
import android.graphics.drawable.Drawable

abstract class LauncherItem {
    abstract val key: String
    abstract val defaultName: String
    abstract val defaultIcon: Drawable

    abstract var name: String
    abstract var icon: Drawable

    abstract fun launch(context: Context)
}
