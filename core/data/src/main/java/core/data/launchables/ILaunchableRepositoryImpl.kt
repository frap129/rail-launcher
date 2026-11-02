package core.data.launchables

import android.os.UserHandle
import core.data.R
import core.data.apps.IAppRepository
import core.data.apps.model.App
import core.data.apps.model.isPrivateApp
import core.data.folders.Folder
import core.data.folders.FolderRecord
import core.data.folders.resolveFolders
import core.data.icons.IIconRepository
import core.data.icons.model.Icon
import core.data.icons.model.badgedFor
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
import core.data.profiles.IPrivateSpaceStatus
import core.data.profiles.IProfileSource
import core.data.profiles.PrivateSpaceStatus
import core.data.profiles.ProfileInfo
import core.data.retryWithExponentialBackoff
import core.data.room.dao.FolderDao
import core.data.room.dao.PopupDao
import core.data.shortcuts.IStandaloneShortcutSource
import core.data.shortcuts.ShortcutKey
import core.data.shortcuts.ShortcutLaunchable
import core.data.shortcuts.StandaloneShortcuts
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

private data class AppListSources(val folderAppList: FolderAppList, val standaloneShortcuts: StandaloneShortcuts)

private data class AppState(val appsOrNull: List<App>?, val privateSpaceStatus: PrivateSpaceStatus)

class ILaunchableRepositoryImpl(
    appRepository: IAppRepository,
    private val iconRepository: IIconRepository,
    private val profileSource: IProfileSource,
    private val preferencesRepository: IPreferencesRepository,
    folderDao: FolderDao,
    popupDao: PopupDao,
    standaloneShortcutSource: IStandaloneShortcutSource,
    private val privateSpaceStatus: IPrivateSpaceStatus,
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

    private val appListSources: Flow<AppListSources> = combine(
        folderAppList,
        standaloneShortcutSource.shortcuts
    ) { folders, shortcuts -> AppListSources(folderAppList = folders, standaloneShortcuts = shortcuts) }

    // Icon pack changes re-resolve every icon, so the pack flow triggers the App state.
    private val appState: Flow<AppState> = combine(
        appRepository.apps,
        privateSpaceStatus.status,
        iconRepository.iconPacks
    ) { appsOrNull, status, _ ->
        AppState(appsOrNull = appsOrNull, privateSpaceStatus = status)
    }

    override val launchableState: StateFlow<LaunchableState> = combine(
        appState,
        preferencesRepository.launchableCustomizations,
        preferencesRepository.settings,
        profileSource.profiles,
        appListSources
    ) { apps, customizations, settings, profiles, sources ->
        resolveState(
            appState = apps,
            customizations = customizations,
            settings = settings,
            profiles = profiles,
            appListSources = sources
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
        appState: AppState,
        customizations: List<LaunchableCustomization>,
        settings: Settings,
        profiles: List<ProfileInfo>,
        appListSources: AppListSources
    ): LaunchableState {
        val apps: List<App> = appState.appsOrNull.orEmpty()
        val profilesByUser: Map<UserHandle, ProfileInfo> = profiles.associateBy { profile -> profile.user }
        val names: LaunchableNames = customizations.toLaunchableNames()
        val listedLaunchables: List<Launchable> = apps
            .map { app -> app.resolve(names = names, settings = settings) } +
            appListSources.standaloneShortcuts.launchables.mapNotNull { shortcut ->
                profilesByUser[shortcut.profile]?.let { profile -> shortcut.resolve(names = names, profileInfo = profile) }
            }
        val resolvedLaunchables: List<Launchable> = listedLaunchables
            .filterNot { launchable ->
                appState.privateSpaceStatus.isLocked &&
                    launchable.isPrivateIn(privateSpace = appState.privateSpaceStatus.profile)
            }
        val resolvedBuiltinGroup: LaunchableGroup.Builtin = LaunchableGroup.Builtin(
            items = builtinGroup.items.map { item -> item.resolve(names = names, settings = settings) }
        )
        val folders: List<Folder> = appListSources.folderAppList.records.resolveFolders(names = names)
        val appListLaunchables: List<Launchable> = resolvedLaunchables
            .filterNot { launchable -> launchable.key in appListSources.folderAppList.memberKeys } + folders
        val groups: List<LaunchableGroup> = appListLaunchables.toLaunchableGroups(
            builtinGroup = resolvedBuiltinGroup,
            privateSpaceStatus = appState.privateSpaceStatus
        )
        val pickerGroups: List<LaunchableGroup.Listed> = (resolvedLaunchables + folders)
            .toLaunchableGroups(
                builtinGroup = resolvedBuiltinGroup,
                privateSpaceStatus = appState.privateSpaceStatus
            )
            .toPickerGroups()
        return LaunchableState(
            groups = groups,
            // Folder members stay reachable through their Folder, pickers and the Favorites.
            launchables = resolvedLaunchables + resolvedBuiltinGroup.items + folders,
            isLoaded = appState.appsOrNull != null,
            keptKeys = appState.privateSpaceStatus.keptKeys,
            retainedKeys = appListSources.standaloneShortcuts.unresolvedKeys.filterTo(mutableSetOf()) { key ->
                ShortcutKey.parse(key)?.profile?.let { profile -> profile in profilesByUser } == true
            },
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

    private fun ShortcutLaunchable.resolve(names: LaunchableNames, profileInfo: ProfileInfo): ShortcutLaunchable {
        val name: String = resolveName(names)
        val badgedIcon: Icon = Icon.ShortcutIcon(packageName = packageName, shortcutId = shortcutId, profile = profile)
            .badgedFor(profile = profileInfo)
        return copy(
            profileInfo = profileInfo,
            defaultIcon = badgedIcon,
            name = name,
            icon = badgedIcon,
            badgedLabel = resolveBadgedLabel(name = name, profileInfo = profileInfo)
        )
    }

    private fun App.badgedLabel(name: String): String = resolveBadgedLabel(
        name = name,
        profileInfo = profileInfo,
        badgedDefaultName = badgedLabel.takeIf { name == defaultName }
    )

    private fun resolveBadgedLabel(name: String, profileInfo: ProfileInfo, badgedDefaultName: String? = null): String = when {
        profileInfo.kind.isPersonal -> name
        badgedDefaultName != null -> badgedDefaultName
        else -> profileSource.badgedLabel(label = name, user = profileInfo.user)
    }
}

private const val DIGIT_GROUP_LABEL: String = "#"

/**
 * Orders the Rail: the Star, then Initial groups sorted by label, then
 * [builtinGroup], then the Private Space container. Names starting with a
 * digit share the "#" group. Private Apps and their Shortcuts belong to the
 * Private Space group only, so they never form or join an Initial group.
 * Folders group with the other Launchables by their resolved names. Always
 * holds the Star and the Builtin group so Home stays reachable without apps.
 *
 * [privateSpaceStatus] supplies the Private Space's Profile and whether its
 * container is listed, Hidden or Locked.
 */
internal fun List<Launchable>.toLaunchableGroups(
    builtinGroup: LaunchableGroup.Builtin,
    privateSpaceStatus: PrivateSpaceStatus = PrivateSpaceStatus()
): List<LaunchableGroup> {
    val privateSpace: ProfileInfo? = privateSpaceStatus.profile
    val (privateItems, otherItems) = partition { launchable -> launchable.isPrivateIn(privateSpace = privateSpace) }
    val initialGroups: List<LaunchableGroup.InitialGroup> = otherItems.groupBy { it.groupLabel() }
        .map { (label: String, items: List<Launchable>) -> LaunchableGroup.InitialGroup(label = label, items = items) }
        .sortedBy { it.label }
    val privateSpaceGroup: LaunchableGroup.PrivateSpace? = when {
        !privateSpaceStatus.isContainerListed -> null

        privateSpaceStatus.isLocked -> LaunchableGroup.PrivateSpace.Locked

        else -> LaunchableGroup.PrivateSpace.Unlocked(
            items = privateItems.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { launchable -> launchable.name })
        )
    }
    return listOf(LaunchableGroup.Star) + initialGroups + builtinGroup + listOfNotNull(privateSpaceGroup)
}

private fun Launchable.groupLabel(): String {
    val initial: Char = name.firstOrNull()?.uppercaseChar() ?: return DIGIT_GROUP_LABEL
    return if (initial.isDigit()) DIGIT_GROUP_LABEL else initial.toString()
}

/** Whether this Launchable belongs to [privateSpace]: a private App or a Shortcut its Profile publishes. */
internal fun Launchable.isPrivateIn(privateSpace: ProfileInfo?): Boolean =
    isPrivateApp || (this is ShortcutLaunchable && profile == privateSpace?.user)
