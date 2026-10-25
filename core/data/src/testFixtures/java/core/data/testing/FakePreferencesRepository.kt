package core.data.testing

import core.data.icons.model.IconPack
import core.data.launchables.model.Launchable
import core.data.prefs.IPreferencesRepository
import core.data.prefs.LaunchableCustomization
import core.data.prefs.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakePreferencesRepository(inputCustomizations: List<LaunchableCustomization> = emptyList(), inputSettings: Settings = Settings()) :
    IPreferencesRepository {
    data class ItemRename(val item: Launchable, val name: String)

    private val recordedRenames: MutableList<ItemRename> = mutableListOf()
    private val recordedResets: MutableList<Launchable> = mutableListOf()
    val renames: List<ItemRename> get() = recordedRenames.toList()
    val resets: List<Launchable> get() = recordedResets.toList()
    override val launchableCustomizations: Flow<List<LaunchableCustomization>> = MutableStateFlow(inputCustomizations)
    override val settings: Flow<Settings> = MutableStateFlow(inputSettings)

    override fun setItemName(item: Launchable, name: String) {
        if (name.isBlank()) {
            resetItemName(item)
            return
        }
        recordedRenames.add(ItemRename(item, name))
    }

    override fun resetItemName(item: Launchable) {
        recordedResets.add(item)
    }

    override fun setIconPack(iconPack: IconPack): Unit = Unit
}
