package core.data.icons

import android.content.ComponentName
import core.data.icons.model.Icon
import core.data.icons.model.IconPack
import core.data.profiles.ProfileInfo
import kotlinx.coroutines.flow.StateFlow

interface IIconRepository {
    val iconPacks: StateFlow<Set<IconPack>>
    suspend fun getIcon(packPackageName: String, componentName: ComponentName, profile: ProfileInfo): Icon
}
