package core.data.icons

import android.content.ComponentName
import core.data.icons.model.CustomIcon
import core.data.icons.model.Icon
import core.data.icons.model.IconPack
import core.data.icons.model.IconPackCategory
import core.data.launchables.model.Launchable
import core.data.profiles.ProfileInfo
import kotlinx.coroutines.flow.Flow

interface IIconRepository {
    /**
     * The installed Icon packs, emitting after every re-read even when the set is unchanged, so
     * Launchable resolution and the Icon picker react to pack updates.
     */
    val iconPacks: Flow<Set<IconPack>>

    suspend fun getIcon(packPackageName: String, componentName: ComponentName, profile: ProfileInfo): Icon

    /** The icon [customIcon] resolves to, badged for [profile] when it is not null, or null when it is gone. */
    suspend fun getCustomIcon(customIcon: CustomIcon, profile: ProfileInfo? = null): Icon?

    /** The categories [packPackageName] lists, in pack order; a pack without headings has one untitled category. */
    suspend fun getPackCategories(packPackageName: String): List<IconPackCategory>

    /** The likely icons for [launchable], emitting again as each installed Icon pack finishes loading. */
    fun getSuggestedIcons(launchable: Launchable): Flow<List<Icon>>
}
