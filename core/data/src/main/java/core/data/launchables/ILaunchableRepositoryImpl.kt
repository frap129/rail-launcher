package core.data.launchables

import core.data.R
import core.data.apps.IAppRepository
import core.data.apps.model.App
import core.data.icons.IIconRepository
import core.data.icons.model.Icon
import core.data.launchables.model.Launchable
import core.data.launchables.model.LaunchableGroup
import core.data.launchables.model.RailLaunchable
import core.data.prefs.IPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class ILaunchableRepositoryImpl(
    appRepository: IAppRepository,
    iconRepository: IIconRepository,
    preferencesRepository: IPreferencesRepository
) : ILaunchableRepository {
    private val builtinLaunchables = LaunchableGroup(
        name = "⌂",
        items = listOf(
            RailLaunchable(
                defaultName = "Rail Settings",
                defaultIcon = Icon.RailIcon(R.drawable.settings_icon),
                deeplink = "rail-launcher://settings/main"
            )
        )
    )

    override val launchables: Flow<List<Launchable>> = combine(appRepository.apps) { itemLists: Array<List<Launchable>> ->
        mutableListOf<Launchable>().apply {
            itemLists.forEach { list -> addAll(list) }
        }
    }

    override val launchableGroups: Flow<List<LaunchableGroup>> = combine(
        launchables,
        preferencesRepository.itemNames,
        iconRepository.iconPacks,
        preferencesRepository.getIconPackName()
    ) { items, names, iconPacks, globalIconPack ->
        items.groupBy { item ->
            item.name = names[item.key] ?: item.defaultName

            if (item is App) {
                item.icon = globalIconPack?.let { iconRepository.getIcon(it, item.componentName) } ?: item.defaultIcon
            }

            item.name.first().uppercaseChar()
        }.map { mapEntry ->
            LaunchableGroup(
                mapEntry.key.toString(),
                mapEntry.value
            )
        }.sortedBy { it.name }.toMutableList().apply {
            if (this.isNotEmpty()) {
                add(builtinLaunchables)
            }
        }
    }
}
