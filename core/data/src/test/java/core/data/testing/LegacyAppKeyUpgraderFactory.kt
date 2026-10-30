package core.data.testing

import core.data.launchables.ILegacyAppKeyUpgrader
import core.data.launchables.ILegacyAppKeyUpgraderImpl
import core.data.room.AppDatabase
import core.data.room.dao.HeaderSettingsDao

/** The production Legacy App key upgrader over [database]'s tables. */
fun createLegacyAppKeyUpgrader(
    database: AppDatabase,
    headerSettingsDao: HeaderSettingsDao = database.getHeaderSettingsDao()
): ILegacyAppKeyUpgrader = ILegacyAppKeyUpgraderImpl(
    favoriteDao = database.getFavoriteDao(),
    launchableCustomizationDao = database.getLaunchableCustomizationDao(),
    headerSettingsDao = headerSettingsDao,
    popupDao = database.getPopupDao(),
    widgetAreaDao = database.getWidgetAreaDao()
)
