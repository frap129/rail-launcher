package core.data.launchables

import core.data.R
import core.data.apps.IAppRepository
import core.data.apps.model.App
import core.data.folders.Folder
import core.data.folders.resolveFolders
import core.data.icons.IIconRepository
import core.data.icons.model.Icon
import core.data.launchables.model.Launchable
import core.data.launchables.model.LaunchableAppearance
import core.data.launchables.model.LaunchableGroup
import core.data.launchables.model.LaunchableNames
import core.data.launchables.model.RailLaunchable
import core.data.prefs.IPreferencesRepository
import core.data.prefs.LaunchableCustomization
import core.data.prefs.Settings
import core.data.prefs.toLaunchableNames
import core.data.retryWithExponentialBackoff
import core.data.room.dao.FolderDao
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
    folderDao: FolderDao,
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
        iconRepository.iconPacks,
        folderDao.observeAll().resolveFolders(customizations = preferencesRepository.launchableCustomizations)
    ) { appsOrNull, customizations, settings, _, folders ->
        resolveState(
            appsOrNull = appsOrNull,
            customizations = customizations,
            settings = settings,
            folders = folders
        )
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
        settings: Settings,
        folders: List<Folder>
    ): LaunchableState {
        val apps: List<App> = appsOrNull.orEmpty()
        val names: LaunchableNames = customizations.toLaunchableNames()
        val resolvedApps: List<Launchable> = apps.map { app -> app.resolve(names = names, settings = settings) }
        val resolvedBuiltinGroup: LaunchableGroup.Builtin = LaunchableGroup.Builtin(
            items = builtinGroup.items.map { item -> item.resolve(names = names, settings = settings) }
        )
        val groups: List<LaunchableGroup> = resolvedApps.toLaunchableGroups(
            builtinGroup = resolvedBuiltinGroup,
            folders = folders
        )
        return LaunchableState(
            groups = groups,
            // Every Folder is flat-listed, whether or not its App List group is shown.
            launchables = groups.toAppListLaunchables() + folders,
            isLoaded = appsOrNull != null
        )
    }

    private suspend fun Launchable.resolve(names: LaunchableNames, settings: Settings): Launchable {
        val name: String = resolveName(names)
        val iconPack: String? = settings.iconPack
        val icon: Icon = if (this is App && iconPack != null) {
            iconRepository.getIcon(packPackageName = iconPack, componentName = componentName)
        } else {
            defaultIcon
        }
        return withCustomization(LaunchableAppearance(name = name, icon = icon))
    }
}

/**
 * Flattens the App List groups into their Launchables in Rail order. The
 * Folders group is skipped because every Folder is flat-listed from its
 * record, whether or not that group shows it.
 */
fun List<LaunchableGroup>.toAppListLaunchables(): List<Launchable> = filterIsInstance<LaunchableGroup.Listed>()
    .filterNot { group -> group is LaunchableGroup.Folders }
    .flatMap { group -> group.items }

private const val DIGIT_GROUP_LABEL: String = "#"

/**
 * Orders the Rail: the Star, then Initial groups sorted by label, then the
 * Folders group when at least one Folder is shown in the App List, then
 * [builtinGroup]. Names starting with a digit share the "#" group. Always
 * holds the Star and the Builtin group so Home stays reachable without apps.
 */
internal fun List<Launchable>.toLaunchableGroups(
    builtinGroup: LaunchableGroup.Builtin,
    folders: List<Folder> = emptyList()
): List<LaunchableGroup> {
    val initialGroups: List<LaunchableGroup.InitialGroup> = groupBy { it.groupLabel() }
        .map { (label: String, items: List<Launchable>) -> LaunchableGroup.InitialGroup(label = label, items = items) }
        .sortedBy { it.label }
    val folderGroup: LaunchableGroup.Folders? = folders
        .filter { folder -> folder.showInAppList }
        .takeIf { items -> items.isNotEmpty() }
        ?.let { items -> LaunchableGroup.Folders(items = items) }
    return listOf(LaunchableGroup.Star) + initialGroups + listOfNotNull(folderGroup) + builtinGroup
}

private fun Launchable.groupLabel(): String {
    val initial: Char = name.firstOrNull()?.uppercaseChar() ?: return DIGIT_GROUP_LABEL
    return if (initial.isDigit()) DIGIT_GROUP_LABEL else initial.toString()
}
