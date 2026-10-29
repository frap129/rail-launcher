package core.data.testing

import core.data.launchables.ILegacyAppKeyUpgrader
import core.data.launchables.ILegacyAppKeyUpgraderImpl
import core.data.room.AppDatabase

/** The production Legacy App key upgrader over [database]'s tables. */
fun createLegacyAppKeyUpgrader(database: AppDatabase): ILegacyAppKeyUpgrader = ILegacyAppKeyUpgraderImpl(
    favoriteDao = database.getFavoriteDao(),
    launchableCustomizationDao = database.getLaunchableCustomizationDao(),
    headerSettingsDao = database.getHeaderSettingsDao(),
    popupDao = database.getPopupDao(),
    widgetAreaDao = database.getWidgetAreaDao()
)
