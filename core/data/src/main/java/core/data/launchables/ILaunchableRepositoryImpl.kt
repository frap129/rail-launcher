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
    private val builtinGroup = LaunchableGroup.Builtin(
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
        preferencesRepository.launchableCustomizations,
        preferencesRepository.settings,
        iconRepository.iconPacks
    ) { items, customizations, settings, iconPacks ->
        val names: Map<String, String?> = customizations.associate { it.key to it.name }
        items.forEach { item ->
            item.name = names[item.key] ?: item.defaultName
            if (item is App) {
                item.icon = settings.iconPack?.let { iconRepository.getIcon(it, item.componentName) } ?: item.defaultIcon
            }
        }
        items.toLaunchableGroups(builtinGroup)
    }
}

private const val DIGIT_GROUP_LABEL: String = "#"

/**
 * Orders the Rail: the Star, then Initial groups sorted by label, then
 * [builtinGroup]. Names starting with a digit share the "#" group. Empty
 * when there are no Launchables.
 */
internal fun List<Launchable>.toLaunchableGroups(builtinGroup: LaunchableGroup.Builtin): List<LaunchableGroup> {
    if (isEmpty()) {
        return emptyList()
    }
    val initialGroups: List<LaunchableGroup.InitialGroup> = groupBy { it.groupLabel() }
        .map { (label: String, items: List<Launchable>) -> LaunchableGroup.InitialGroup(label = label, items = items) }
        .sortedBy { it.label }
    return listOf(LaunchableGroup.Star) + initialGroups + builtinGroup
}

private fun Launchable.groupLabel(): String {
    val initial: Char = name.firstOrNull()?.uppercaseChar() ?: return DIGIT_GROUP_LABEL
    return if (initial.isDigit()) DIGIT_GROUP_LABEL else initial.toString()
}
