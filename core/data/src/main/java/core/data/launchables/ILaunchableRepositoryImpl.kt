package core.data.launchables

import core.data.R
import core.data.apps.IAppRepository
import core.data.apps.model.App
import core.data.apps.model.isPrivateApp
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
import core.data.launchables.model.toPickerGroups
import core.data.prefs.IPreferencesRepository
import core.data.prefs.LaunchableCustomization
import core.data.prefs.Settings
import core.data.prefs.toLaunchableNames
import core.data.profiles.IProfileSource
import core.data.profiles.ProfileInfo
import core.data.profiles.ProfileToken
import core.data.profiles.isPrivate
import core.data.profiles.privateSpace
import core.data.profiles.toProfileToken
import core.data.retryWithExponentialBackoff
import core.data.room.dao.FolderDao
import core.data.room.dao.PopupDao
import core.data.runCleanup
import core.util.WHILE_SUBSCRIBED_TIMEOUT_MS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private data class FolderAppList(val records: List<FolderRecord>, val memberKeys: Set<String>)

class ILaunchableRepositoryImpl(
    appRepository: IAppRepository,
    private val iconRepository: IIconRepository,
    private val profileSource: IProfileSource,
    private val preferencesRepository: IPreferencesRepository,
    folderDao: FolderDao,
    popupDao: PopupDao,
    private val scope: CoroutineScope
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

    // Icon pack changes re-resolve every icon, so the pack flow triggers the App state.
    private val appsWithIconPacks: Flow<List<App>?> = combine(appRepository.apps, iconRepository.iconPacks) { apps, _ -> apps }

    override val launchableState: StateFlow<LaunchableState> = combine(
        appsWithIconPacks,
        preferencesRepository.launchableCustomizations,
        preferencesRepository.settings,
        profileSource.profiles,
        folderAppList
    ) { appsOrNull, customizations, settings, profiles, folders ->
        resolveState(
            appsOrNull = appsOrNull,
            customizations = customizations,
            settings = settings,
            profiles = profiles,
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

    init {
        // Deleting stale names keeps a reinstalled Launchable from restoring an old rename.
        scope.launch {
            combine(
                preferencesRepository.launchableCustomizations,
                launchableState
            ) { customizations, state -> customizations to state }
                .retryWithExponentialBackoff()
                .collect { (customizations, state) ->
                    if (state.isLoaded) {
                        removeStaleNames(state = state, customizations = customizations)
                    }
                }
        }
    }

    private suspend fun removeStaleNames(state: LaunchableState, customizations: List<LaunchableCustomization>) {
        val staleKeys: List<String> = state.staleKeys(customizations.map { customization -> customization.key })
        if (staleKeys.isEmpty()) {
            return
        }
        runCleanup(message = "Failed to remove ${staleKeys.size} stale name customizations") {
            preferencesRepository.removeLaunchableNames(keys = staleKeys)
        }
    }

    private suspend fun resolveState(
        appsOrNull: List<App>?,
        customizations: List<LaunchableCustomization>,
        settings: Settings,
        profiles: List<ProfileInfo>,
        folderAppList: FolderAppList
    ): LaunchableState {
        val apps: List<App> = appsOrNull.orEmpty()
        val keptKeys: Set<ProfileToken> = profiles
            .filter { profile -> profile.isPrivate && profile.isQuiet }
            .mapTo(mutableSetOf()) { profile -> profile.user.toProfileToken() }
        val names: LaunchableNames = customizations.toLaunchableNames()
        val resolvedApps: List<Launchable> = apps.map { app -> app.resolve(names = names, settings = settings) }
        val resolvedBuiltinGroup: LaunchableGroup.Builtin = LaunchableGroup.Builtin(
            items = builtinGroup.items.map { item -> item.resolve(names = names, settings = settings) }
        )
        val folders: List<Folder> = folderAppList.records.resolveFolders(names = names)
        val appListLaunchables: List<Launchable> =
            resolvedApps.filterNot { app -> app.key in folderAppList.memberKeys } + folders
        val privateSpace: ProfileInfo? = profiles.privateSpace
        val groups: List<LaunchableGroup> = appListLaunchables.toLaunchableGroups(
            builtinGroup = resolvedBuiltinGroup,
            privateSpace = privateSpace
        )
        val pickerGroups: List<LaunchableGroup.Listed> = (resolvedApps + folders)
            .toLaunchableGroups(builtinGroup = resolvedBuiltinGroup, privateSpace = privateSpace)
            .toPickerGroups()
        return LaunchableState(
            groups = groups,
            // Folder members stay reachable through their Folder, pickers and the Favorites.
            launchables = resolvedApps + resolvedBuiltinGroup.items + folders,
            isLoaded = appsOrNull != null,
            keptKeys = keptKeys,
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
 * [builtinGroup], then the Private Space container. Names starting with a
 * digit share the "#" group. Private Apps belong to the Private Space group
 * only, so they never form or join an Initial group. Folders group with the
 * other Launchables by their resolved names. Always holds the Star and the
 * Builtin group so Home stays reachable without apps.
 *
 * [privateSpace] is the Profile of the Private Space, or null when the device
 * has none; its quiet mode makes the container Locked.
 */
internal fun List<Launchable>.toLaunchableGroups(
    builtinGroup: LaunchableGroup.Builtin,
    privateSpace: ProfileInfo? = null
): List<LaunchableGroup> {
    val (privateApps, otherItems) = partition { launchable -> launchable.isPrivateApp }
    val initialGroups: List<LaunchableGroup.InitialGroup> = otherItems.groupBy { it.groupLabel() }
        .map { (label: String, items: List<Launchable>) -> LaunchableGroup.InitialGroup(label = label, items = items) }
        .sortedBy { it.label }
    val privateSpaceGroup: LaunchableGroup.PrivateSpace? = privateSpace?.let { profile ->
        if (profile.isQuiet) {
            LaunchableGroup.PrivateSpace.Locked
        } else {
            LaunchableGroup.PrivateSpace.Unlocked(
                items = privateApps.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { launchable -> launchable.name })
            )
        }
    }
    return listOf(LaunchableGroup.Star) + initialGroups + builtinGroup + listOfNotNull(privateSpaceGroup)
}

private fun Launchable.groupLabel(): String {
    val initial: Char = name.firstOrNull()?.uppercaseChar() ?: return DIGIT_GROUP_LABEL
    return if (initial.isDigit()) DIGIT_GROUP_LABEL else initial.toString()
}
