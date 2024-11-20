package core.data.launchables

import androidx.datastore.preferences.core.stringPreferencesKey
import core.data.R
import core.data.apps.App
import core.data.apps.AppRepository
import core.data.icons.IconRepository
import core.data.icons.model.Icon
import core.data.icons.model.IconPack
import core.data.launchables.model.Launchable
import core.data.launchables.model.LaunchableGroup
import core.data.launchables.model.RailLaunchable
import core.data.prefs.PreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest

class LaunchableRepository(appRepository: AppRepository, iconRepository: IconRepository, preferencesRepository: PreferencesRepository) {
    private val builtinLaunchables = LaunchableGroup(
        name = "⌂",
        items = listOf(
            RailLaunchable(
                defaultName = "Rail Settings",
                defaultIcon = Icon.RailIcon(R.drawable.settings_icon),
                deeplink = "rail-launcher://settings"
            )
        )
    )

    val launchables = combine(appRepository.apps) { itemLists: Array<List<Launchable>> ->
        mutableListOf<Launchable>().apply {
            itemLists.forEach { list -> addAll(list) }
        }
    }

    val launchableGroups: Flow<List<LaunchableGroup>> = combine(
        launchables,
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
                item.icon = defaultIconPack?.let { iconRepository.getIcon(it, item.componentName) } ?: item.defaultIcon
            }

            item.name.first().uppercaseChar()
        }.map { mapEntry ->
            LaunchableGroup(
                mapEntry.key.toString(),
                mapEntry.value
            )
        }.sortedBy { it.name }.toMutableList().apply {
            if (this.isNotEmpty())
                add(builtinLaunchables)
            }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getItemByKey(key: String): Flow<Launchable?> = launchables.mapLatest { items ->
        items.find { it.key == key }
    }.flowOn(Dispatchers.IO)
}
