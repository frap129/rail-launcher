package core.data.prefs

import core.data.icons.model.IconPack
import core.data.launchables.model.Launchable
import core.data.room.dao.LaunchableCustomizationDao
import core.data.room.dao.SettingsDao
import core.util.launchInBackground
import kotlinx.coroutines.flow.Flow

class IPreferencesRepositoryImpl(private val launchableCustomizationDao: LaunchableCustomizationDao, private val settingsDao: SettingsDao) :
    IPreferencesRepository {
    override val launchableCustomizations: Flow<List<LaunchableCustomization>> = launchableCustomizationDao.observeAll()
    override val settings: Flow<Settings> = settingsDao.observe()

    override fun setItemName(item: Launchable, name: String) {
        launchInBackground {
            launchableCustomizationDao.setName(key = item.key, name = name)
        }
    }

    override fun setIconPack(iconPack: IconPack) {
        launchInBackground {
            settingsDao.upsert(Settings(iconPack = (iconPack as? IconPack.CustomIconPack)?.packageName))
        }
    }
}
