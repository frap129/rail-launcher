package feature.launcher

import androidx.datastore.preferences.core.Preferences
import core.data.icons.model.IconPack
import core.data.launchables.model.Launchable
import core.data.prefs.IPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf

class FakePreferencesRepository : IPreferencesRepository {
    data class ItemRename(val item: Launchable, val name: String)

    private val recordedRenames: MutableList<ItemRename> = mutableListOf()
    val renames: List<ItemRename> get() = recordedRenames.toList()
    override val itemNames: Flow<Preferences> = emptyFlow()
    override val itemIcons: Flow<Preferences> = emptyFlow()

    override fun getItemName(item: Launchable): Flow<String> = flowOf(item.name)

    override fun setItemName(item: Launchable, name: String) {
        recordedRenames.add(ItemRename(item, name))
    }

    override fun getIconPackName(): Flow<String?> = flowOf(null)

    override fun setIconPack(iconPack: IconPack) = Unit
}
