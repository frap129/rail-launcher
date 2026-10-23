package core.data.prefs

import core.data.icons.model.IconPack
import core.data.launchables.model.Launchable
import core.data.room.dao.LaunchableCustomizationDao
import core.data.room.dao.SettingsDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class IPreferencesRepositoryImpl(
    private val launchableCustomizationDao: LaunchableCustomizationDao,
    private val settingsDao: SettingsDao,
    private val scope: CoroutineScope
) : IPreferencesRepository {
    override val launchableCustomizations: Flow<List<LaunchableCustomization>> = launchableCustomizationDao.observeAll()
    override val settings: Flow<Settings> = settingsDao.observe()

    override fun setItemName(item: Launchable, name: String) {
        scope.launch(Dispatchers.IO) {
            launchableCustomizationDao.setName(key = item.key, name = name)
        }
    }

    override fun setIconPack(iconPack: IconPack) {
        scope.launch(Dispatchers.IO) {
            settingsDao.upsert(Settings(iconPack = (iconPack as? IconPack.CustomIconPack)?.packageName))
        }
    }
}
