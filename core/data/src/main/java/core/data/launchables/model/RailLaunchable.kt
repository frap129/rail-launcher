package core.data.launchables.model

import android.content.Context
import android.content.Intent
import android.net.Uri
import core.data.icons.model.Icon

class RailLaunchable(override val defaultName: String, override val defaultIcon: Icon, private val deeplink: String) : Launchable() {
    override val key = deeplink
    override var name = defaultName
    override var icon: Icon = defaultIcon

    override fun launch(context: Context) = context.startActivity(
        Intent().apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse(deeplink)
        }
    )
}

