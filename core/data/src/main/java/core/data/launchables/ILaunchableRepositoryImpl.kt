package core.data.launchables

import core.data.R
import core.data.apps.IAppRepository
import core.data.apps.model.App
import core.data.folders.Folder
import core.data.folders.FolderRecord
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
import core.data.profiles.IProfileSource
import core.data.retryWithExponentialBackoff
import core.data.room.dao.FolderDao
import core.data.room.dao.PopupDao
import core.util.WHILE_SUBSCRIBED_TIMEOUT_MS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

private data class FolderAppList(val records: List<FolderRecord>, val memberKeys: Set<String>)

class ILaunchableRepositoryImpl(
    appRepository: IAppRepository,
    private val iconRepository: IIconRepository,
    private val profileSource: IProfileSource,
    preferencesRepository: IPreferencesRepository,
    folderDao: FolderDao,
    popupDao: PopupDao,
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

    private val folderAppList: Flow<FolderAppList> = combine(
        folderDao.observeAll(),
        popupDao.observeAllItems()
    ) { records, items ->
        val folderKeys: Set<String> = records.mapTo(mutableSetOf()) { record -> Folder(folderId = record.id).key }
        FolderAppList(
            records = records,
            memberKeys = items
                .asSequence()
                .filter { item -> item.ownerKey in folderKeys }
                .mapTo(mutableSetOf()) { item -> item.itemKey }
        )
    }.distinctUntilChanged()

    override val launchableState: StateFlow<LaunchableState> = combine(
        appRepository.apps,
        preferencesRepository.launchableCustomizations,
        preferencesRepository.settings,
        iconRepository.iconPacks,
        folderAppList
    ) { appsOrNull, customizations, settings, _, folders ->
        resolveState(
            appsOrNull = appsOrNull,
            customizations = customizations,
            settings = settings,
            folderAppList = folders
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
        folderAppList: FolderAppList
    ): LaunchableState {
        val apps: List<App> = appsOrNull.orEmpty()
        val names: LaunchableNames = customizations.toLaunchableNames()
        val resolvedApps: List<Launchable> = apps.map { app -> app.resolve(names = names, settings = settings) }
        val resolvedBuiltinGroup: LaunchableGroup.Builtin = LaunchableGroup.Builtin(
            items = builtinGroup.items.map { item -> item.resolve(names = names, settings = settings) }
        )
        val folders: List<Folder> = folderAppList.records.resolveFolders(names = names)
        val appListLaunchables: List<Launchable> =
            resolvedApps.filterNot { app -> app.key in folderAppList.memberKeys } + folders
        val groups: List<LaunchableGroup> = appListLaunchables.toLaunchableGroups(builtinGroup = resolvedBuiltinGroup)
        val pickerGroups: List<LaunchableGroup.Listed> =
            (resolvedApps + folders).toLaunchableGroups(builtinGroup = resolvedBuiltinGroup).filterIsInstance<LaunchableGroup.Listed>()
        return LaunchableState(
            groups = groups,
            // Folder members stay reachable through their Folder, pickers and the Favorites.
            launchables = resolvedApps + resolvedBuiltinGroup.items + folders,
            isLoaded = appsOrNull != null,
            pickerGroups = pickerGroups
        )
    }

    private suspend fun Launchable.resolve(names: LaunchableNames, settings: Settings): Launchable {
        val name: String = resolveName(names)
        val iconPack: String? = settings.iconPack
        val icon: Icon = if (this is App && iconPack != null) {
            iconRepository.getIcon(packPackageName = iconPack, componentName = componentName, profile = profileInfo)
        } else {
            defaultIcon
        }
        return if (this is App) {
            copy(name = name, icon = icon, badgedLabel = badgedLabel(name = name))
        } else {
            withCustomization(LaunchableAppearance(name = name, icon = icon))
        }
    }

    private fun App.badgedLabel(name: String): String = when {
        profileInfo.kind.isPersonal -> name
        name == defaultName -> badgedLabel
        else -> profileSource.badgedLabel(label = name, user = profile)
    }
}

private const val DIGIT_GROUP_LABEL: String = "#"

/**
 * Orders the Rail: the Star, then Initial groups sorted by label, then
 * [builtinGroup]. Names starting with a digit share the "#" group. Folders
 * group with the other Launchables by their resolved names. Always holds the
 * Star and the Builtin group so Home stays reachable without apps.
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
