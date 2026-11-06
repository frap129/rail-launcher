package core.data.icons

import android.content.ComponentName
import core.data.icons.model.CustomIcon
import core.data.icons.model.Icon
import core.data.icons.model.IconPack
import core.data.profiles.ProfileInfo
import kotlinx.coroutines.flow.StateFlow

interface IIconRepository {
    val iconPacks: StateFlow<Set<IconPack>>

    suspend fun getIcon(packPackageName: String, componentName: ComponentName, profile: ProfileInfo): Icon

    /** The pack drawable [customIcon] resolves to, badged for [profile], or null when it is gone. */
    suspend fun getCustomIcon(customIcon: CustomIcon, profile: ProfileInfo): Icon?

    /** The icons [packPackageName] offers, in the order its drawable list gives them. */
    suspend fun getPackIcons(packPackageName: String): List<Icon.PackDrawableIcon>
}
