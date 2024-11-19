package core.data.launcher

import androidx.datastore.preferences.core.stringPreferencesKey
import core.data.apps.App
import core.data.apps.AppRepository
import core.data.icons.IconRepository
import core.data.icons.model.IconPack
import core.data.launcher.model.LauncherItem
import core.data.launcher.model.LauncherItemGroup
import core.data.prefs.PreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest

class LauncherItemRepository(appRepository: AppRepository, iconRepository: IconRepository, preferencesRepository: PreferencesRepository) {
    val launcherItems = combine(appRepository.apps) { itemLists: Array<List<LauncherItem>> ->
        mutableListOf<LauncherItem>().apply {
            itemLists.forEach { list -> addAll(list) }
        }
    }

    val launcherItemGroups: Flow<List<LauncherItemGroup>> = combine(
        launcherItems,
        preferencesRepository.itemNames,
        preferencesRepository.itemIcons,
        iconRepository.iconPacks,
        preferencesRepository.getIconPackName()
    ) { items, names, icons, iconPacks, defaultIconPack ->
        val iconPack: IconPack.CustomIconPack? = iconPacks.find {
            it is IconPack.CustomIconPack && it.packageName == defaultIconPack
        } as IconPack.CustomIconPack?

        items.groupBy { item ->
            item.name = names[stringPreferencesKey(item.key)] ?: item.defaultName

            if (item is App) {
                item.icon = iconPack?.packageName?.let { iconRepository.getIcon(it, item.componentName) } ?: item.defaultIcon
            }

            item.name.first().uppercaseChar()
        }.map { mapEntry ->
            LauncherItemGroup(
                mapEntry.key.toString(),
                mapEntry.value
            )
        }.sortedBy { it.name }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getItemByKey(key: String): Flow<LauncherItem?> = launcherItems.mapLatest { items ->
        items.find { it.key == key }
    }.flowOn(Dispatchers.IO)
}
