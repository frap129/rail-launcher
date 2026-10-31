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
        if (name.isBlank()) {
            resetItemName(item)
            return
        }
        scope.launch(Dispatchers.IO) {
            launchableCustomizationDao.setName(key = item.key, name = name)
        }
    }

    override fun resetItemName(item: Launchable) {
        scope.launch(Dispatchers.IO) {
            launchableCustomizationDao.delete(key = item.key)
        }
    }

    override suspend fun removeLaunchableNames(keys: List<String>) {
        if (keys.isEmpty()) {
            return
        }
        launchableCustomizationDao.delete(keys = keys)
    }

    override fun setIconPack(iconPack: IconPack) {
        scope.launch(Dispatchers.IO) {
            val packageName: String? = (iconPack as? IconPack.CustomIconPack)?.packageName
            settingsDao.update { settings -> settings.copy(iconPack = packageName) }
        }
    }

    override fun setHidePrivateSpaceWhenLocked(hide: Boolean) {
        scope.launch(Dispatchers.IO) {
            settingsDao.update { settings -> settings.copy(hidePrivateSpaceWhenLocked = hide) }
        }
    }
}
