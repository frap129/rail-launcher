package core.data.launchables

import core.data.apps.model.App
import core.data.popups.normalizeItemPositions
import core.data.prefs.HeaderSettings
import core.data.prefs.HeaderTapSetting
import core.data.prefs.withResolvedTapTargets
import core.data.room.dao.FavoriteDao
import core.data.room.dao.HeaderSettingsDao
import core.data.room.dao.LaunchableCustomizationDao
import core.data.room.dao.PopupDao
import core.data.room.dao.WidgetAreaDao

/**
 * Moves every stored package-level App key to the current key of [App]'s package representative.
 * A canonical row that already exists wins, so a change made before the upgrade is not overwritten.
 */
class ILegacyAppKeyUpgraderImpl(
    private val favoriteDao: FavoriteDao,
    private val launchableCustomizationDao: LaunchableCustomizationDao,
    private val headerSettingsDao: HeaderSettingsDao,
    private val popupDao: PopupDao,
    private val widgetAreaDao: WidgetAreaDao
) : ILegacyAppKeyUpgrader {
    override suspend fun upgrade(apps: List<App>) {
        val resolver: LaunchableKeyResolver = LaunchableKeyResolver(launchables = apps)
        val headerSettings: HeaderSettings? = headerSettingsDao.getRow()
        val replacements: Map<String, String> = resolver.legacyReplacements(keys = storedKeys(headerSettings = headerSettings))
        if (replacements.isEmpty()) {
            return
        }
        for ((oldKey: String, newKey: String) in replacements) {
            favoriteDao.replaceKey(oldKey = oldKey, newKey = newKey)
            launchableCustomizationDao.replaceKey(oldKey = oldKey, newKey = newKey)
            popupDao.replaceOwnerKey(oldKey = oldKey, newKey = newKey)
            popupDao.replaceItemKey(oldKey = oldKey, newKey = newKey)
            popupDao.replaceHiddenShortcutOwnerKey(oldKey = oldKey, newKey = newKey)
            widgetAreaDao.replacePopupOwnerKey(oldKey = oldKey, newKey = newKey)
        }
        replacements.values.distinct().forEach { ownerKey -> normalizeItemPositions(popupDao = popupDao, ownerKey = ownerKey) }
        headerSettingsDao.updateIfChanged { settings -> settings.withResolvedTapTargets(replacements = replacements) }
    }

    private suspend fun storedKeys(headerSettings: HeaderSettings?): Set<String> = buildSet {
        addAll(favoriteDao.getAllKeys())
        addAll(launchableCustomizationDao.getAllKeys())
        popupDao.getAllItems().forEach { item ->
            add(item.ownerKey)
            add(item.itemKey)
        }
        addAll(popupDao.getAllHiddenOwnerKeys())
        addAll(widgetAreaDao.getPopupOwnerKeys())
        headerSettings?.tapTargetKeys()?.let(::addAll)
    }
}

private fun HeaderSettings.tapTargetKeys(): List<String> = listOfNotNull(
    (timeTapTarget as? HeaderTapSetting.Launchable)?.key,
    (dateTapTarget as? HeaderTapSetting.Launchable)?.key
)
