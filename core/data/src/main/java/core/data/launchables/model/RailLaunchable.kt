package core.data.launchables.model

import android.content.Context
import android.content.Intent
import android.net.Uri
import core.data.icons.model.Icon

data class RailLaunchable(
    override val defaultName: String,
    override val defaultIcon: Icon,
    private val deeplink: String,
    override val name: String = defaultName,
    override val icon: Icon = defaultIcon
) : Launchable() {
    override val key: String = deeplink

    override fun withCustomization(appearance: LaunchableAppearance): RailLaunchable = copy(name = appearance.name, icon = appearance.icon)

    override fun launch(context: Context) = context.startActivity(
        Intent().apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse(deeplink)
        }
    )
}
