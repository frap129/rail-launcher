package core.data.launchables

import core.data.R
import core.data.apps.IAppRepository
import core.data.apps.model.App
import core.data.icons.IIconRepository
import core.data.icons.model.Icon
import core.data.launchables.model.Launchable
import core.data.launchables.model.LaunchableAppearance
import core.data.launchables.model.LaunchableGroup
import core.data.launchables.model.RailLaunchable
import core.data.prefs.IPreferencesRepository
import core.data.prefs.LaunchableCustomization
import core.data.prefs.Settings
import core.data.retryWithExponentialBackoff
import core.util.WHILE_SUBSCRIBED_TIMEOUT_MS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class ILaunchableRepositoryImpl(
    appRepository: IAppRepository,
    private val iconRepository: IIconRepository,
    preferencesRepository: IPreferencesRepository,
    scope: CoroutineScope
) : ILaunchableRepository {
    private val builtinGroup: LaunchableGroup.Builtin = LaunchableGroup.Builtin(
        items = listOf(
            RailLaunchable(
                defaultName = "Rail Settings",
                defaultIcon = Icon.RailIcon(R.drawable.settings_icon),
                deeplink = "rail-launcher://settings/main"
            )
        )
    )

    override val launchableState: StateFlow<LaunchableState> = combine(
        appRepository.apps,
        preferencesRepository.launchableCustomizations,
        preferencesRepository.settings,
        iconRepository.iconPacks
    ) { appsOrNull, customizations, settings, _ ->
        resolveState(appsOrNull = appsOrNull, customizations = customizations, settings = settings)
    }
        .retryWithExponentialBackoff()
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(WHILE_SUBSCRIBED_TIMEOUT_MS),
            initialValue = LaunchableState(
                groups = emptyList(),
                launchables = emptyList(),
                isLoaded = false
            )
        )

    override val launchables: Flow<List<Launchable>> = launchableState.map { state -> state.launchables }

    private suspend fun resolveState(
        appsOrNull: List<App>?,
        customizations: List<LaunchableCustomization>,
        settings: Settings
    ): LaunchableState {
        val apps: List<App> = appsOrNull.orEmpty()
        val names: Map<String, String?> = customizations.associate { it.key to it.name }
        val resolvedApps: List<Launchable> = apps.map { app -> app.resolve(names = names, settings = settings) }
        val resolvedBuiltinGroup: LaunchableGroup.Builtin = LaunchableGroup.Builtin(
            items = builtinGroup.items.map { item -> item.resolve(names = names, settings = settings) }
        )
        val groups: List<LaunchableGroup> = resolvedApps.toLaunchableGroups(resolvedBuiltinGroup)
        return LaunchableState(
            groups = groups,
            launchables = groups.toLaunchables(),
            isLoaded = appsOrNull != null
        )
    }

    private suspend fun Launchable.resolve(names: Map<String, String?>, settings: Settings): Launchable {
        val name: String = names[key] ?: defaultName
        val iconPack: String? = settings.iconPack
        val icon: Icon = if (this is App && iconPack != null) {
            iconRepository.getIcon(packPackageName = iconPack, componentName = componentName)
        } else {
            defaultIcon
        }
        return withCustomization(LaunchableAppearance(name = name, icon = icon))
    }
}

/** Flattens the Listed groups into their Launchables in Rail order. */
internal fun List<LaunchableGroup>.toLaunchables(): List<Launchable> =
    filterIsInstance<LaunchableGroup.Listed>().flatMap { group -> group.items }

private const val DIGIT_GROUP_LABEL: String = "#"

/**
 * Orders the Rail: the Star, then Initial groups sorted by label, then
 * [builtinGroup]. Names starting with a digit share the "#" group. Always
 * holds the Star and the Builtin group so Home stays reachable without apps.
 */
internal fun List<Launchable>.toLaunchableGroups(builtinGroup: LaunchableGroup.Builtin): List<LaunchableGroup> {
    val initialGroups: List<LaunchableGroup.InitialGroup> = groupBy { it.groupLabel() }
        .map { (label: String, items: List<Launchable>) -> LaunchableGroup.InitialGroup(label = label, items = items) }
        .sortedBy { it.label }
    return listOf(LaunchableGroup.Star) + initialGroups + builtinGroup
}

private fun Launchable.groupLabel(): String {
    val initial: Char = name.firstOrNull()?.uppercaseChar() ?: return DIGIT_GROUP_LABEL
    return if (initial.isDigit()) DIGIT_GROUP_LABEL else initial.toString()
}
