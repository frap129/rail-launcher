package core.data.launchables.model

import android.content.Context
import core.data.icons.model.Icon

abstract class Launchable {
    abstract val key: String
    abstract val defaultName: String
    abstract val defaultIcon: Icon

    abstract var name: String
    abstract var icon: Icon

    abstract fun launch(context: Context)
}
