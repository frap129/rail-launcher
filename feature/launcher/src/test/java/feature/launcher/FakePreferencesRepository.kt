package feature.launcher

import core.data.icons.model.IconPack
import core.data.launchables.model.Launchable
import core.data.prefs.IPreferencesRepository
import core.data.prefs.LaunchableCustomization
import core.data.prefs.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class FakePreferencesRepository : IPreferencesRepository {
    data class ItemRename(val item: Launchable, val name: String)

    private val recordedRenames: MutableList<ItemRename> = mutableListOf()
    val renames: List<ItemRename> get() = recordedRenames.toList()
    override val launchableCustomizations: Flow<List<LaunchableCustomization>> = flowOf(emptyList())
    override val settings: Flow<Settings> = flowOf(Settings())

    override fun setItemName(item: Launchable, name: String) {
        recordedRenames.add(ItemRename(item, name))
    }

    override fun setIconPack(iconPack: IconPack) = Unit
}
