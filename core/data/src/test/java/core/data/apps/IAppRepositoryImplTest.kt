package core.data.apps

import android.content.ComponentName
import android.os.UserHandle
import core.data.apps.model.App
import core.data.favorites.IFavoritesRepositoryImpl
import core.data.icons.model.Icon
import core.data.launchables.ILaunchableRepositoryImpl
import core.data.prefs.LaunchableCustomization
import core.data.profiles.IProfileSource
import core.data.profiles.ProfileActivity
import core.data.profiles.ProfileChange
import core.data.profiles.ProfileInfo
import core.data.profiles.ProfileKind
import core.data.testing.FIRST_POSITION
import core.data.testing.InMemoryDatabaseTest
import core.data.testing.PERSONAL_USER_ID
import core.data.testing.PRIVATE_USER_ID
import core.data.testing.ProfileSourceState
import core.data.testing.SECOND_POSITION
import core.data.testing.WORK_USER_ID
import core.data.testing.createApp
import core.data.testing.createFavoritesRepository
import core.data.testing.createIconRepositoryMock
import core.data.testing.createPreferencesRepositoryMock
import core.data.testing.createPrivateSpaceLock
import core.data.testing.createProfileInfo
import core.data.testing.createProfileSourceMock
import core.data.testing.createUserHandle
import core.data.testing.packageLevelKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.RuntimeEnvironment

private const val PERSONAL_PACKAGE: String = "com.example.personal"
private const val SECOND_PERSONAL_PACKAGE: String = "com.example.second"
private const val WORK_PACKAGE: String = "com.example.work"
private const val NEW_WORK_PACKAGE: String = "com.example.new.work"
private const val SHARED_PACKAGE: String = "com.example.shared"
private const val PRIVATE_PACKAGE: String = "com.example.private"
private const val NEW_PRIVATE_PACKAGE: String = "com.example.new.private"
private const val RENAMED_NAME: String = "Renamed"
private const val POLICY_ACTIVITY_CLASS: String = "PolicyManagementActivity"
private const val SETUP_ACTIVITY_CLASS: String = "SetupManagementLaunchActivity"

@OptIn(ExperimentalCoroutinesApi::class)
class IAppRepositoryImplTest : InMemoryDatabaseTest() {
    private val personalUser: UserHandle = createUserHandle(userId = PERSONAL_USER_ID)
    private val workUser: UserHandle = createUserHandle(userId = WORK_USER_ID)
    private val privateUser: UserHandle = createUserHandle(userId = PRIVATE_USER_ID)
    private val personalProfile: ProfileInfo = ProfileInfo(
        user = personalUser,
        serial = PERSONAL_USER_ID.toLong(),
        kind = ProfileKind.Personal,
        isQuiet = false
    )
    private val workProfile: ProfileInfo = ProfileInfo(
        user = workUser,
        serial = WORK_USER_ID.toLong(),
        kind = ProfileKind.Work,
        isQuiet = false
    )

    @Test
    fun `carries each App's Profile kind, badged label and availability`() = databaseTest {
        val inputPausedWorkProfile: ProfileInfo = workProfile.copy(isQuiet = true)
        val inputState: ProfileSourceState = ProfileSourceState(profiles = listOf(personalProfile, inputPausedWorkProfile))
        inputState.setActivities(
            user = personalUser,
            activities = arrayOf(
                profileActivity(packageName = PERSONAL_PACKAGE, user = personalUser, label = "Gmail", badgedLabel = "Gmail")
            )
        )
        inputState.setActivities(
            user = workUser,
            activities = arrayOf(
                profileActivity(packageName = WORK_PACKAGE, user = workUser, label = "Gmail", badgedLabel = "Work Gmail")
            )
        )
        val repository: IAppRepositoryImpl = createRepository(state = inputState)

        val actualApps: List<App> = loadApps(repository = repository)

        val actualPersonalApp: App = actualApps.single { app -> app.profile == personalUser }
        assertEquals(ProfileKind.Personal, actualPersonalApp.kind)
        assertEquals("Gmail", actualPersonalApp.badgedLabel)
        assertFalse(actualPersonalApp.isPaused)
        val actualWorkApp: App = actualApps.single { app -> app.profile == workUser }
        assertEquals(ProfileKind.Work, actualWorkApp.kind)
        assertEquals("Work Gmail", actualWorkApp.badgedLabel)
        assertTrue(actualWorkApp.isPaused)
    }

    @Test
    fun `marks Apps unavailable when their Profile pauses and available again when it resumes`() = databaseTest {
        val inputState: ProfileSourceState = baseState()
        val repository: IAppRepositoryImpl = createRepository(state = inputState)
        finishPendingWork()

        inputState.profiles.value = listOf(personalProfile, workProfile.copy(isQuiet = true))
        inputState.changes.emit(ProfileChange.ProfileUnavailable(user = workUser))
        assertTrue(loadApps(repository = repository).single { app -> app.profile == workUser }.isPaused)

        inputState.profiles.value = listOf(personalProfile, workProfile)
        inputState.changes.emit(ProfileChange.ProfileAvailable(user = workUser))
        assertFalse(loadApps(repository = repository).single { app -> app.profile == workUser }.isPaused)
    }

    @Test
    fun `does not reload a Profile's Apps when it resumes`() = databaseTest {
        val inputState: ProfileSourceState = ProfileSourceState(
            profiles = listOf(personalProfile, workProfile.copy(isQuiet = true))
        ).apply {
            setActivities(
                user = workUser,
                activities = arrayOf(profileActivity(packageName = WORK_PACKAGE, user = workUser))
            )
        }
        val repository: IAppRepositoryImpl = createRepository(state = inputState)
        finishPendingWork()
        inputState.setActivities(
            user = workUser,
            activities = arrayOf(profileActivity(packageName = WORK_PACKAGE, user = workUser, label = RENAMED_NAME))
        )

        inputState.profiles.value = listOf(personalProfile, workProfile)
        inputState.changes.emit(ProfileChange.ProfileAvailable(user = workUser))
        val actualWorkApp: App = loadApps(repository = repository).single { app -> app.profile == workUser }

        assertFalse(actualWorkApp.isPaused)
        assertEquals(WORK_PACKAGE, actualWorkApp.defaultName)
    }

    @Test
    fun `lists every launcher activity as its own App`() = databaseTest {
        val inputState: ProfileSourceState = ProfileSourceState(profiles = listOf(personalProfile))
        inputState.setActivities(
            user = personalUser,
            activities = profileActivities(
                packageName = PERSONAL_PACKAGE,
                user = personalUser,
                activityClasses = listOf(SETUP_ACTIVITY_CLASS, POLICY_ACTIVITY_CLASS)
            )
        )
        val repository: IAppRepositoryImpl = createRepository(state = inputState)

        val actualApps: List<App> = loadApps(repository = repository)

        assertEquals(
            listOf(SETUP_ACTIVITY_CLASS, POLICY_ACTIVITY_CLASS),
            actualApps.map { app -> app.componentName.className }
        )
        assertEquals(actualApps.size, actualApps.map { app -> app.key }.distinct().size)
        assertTrue(actualApps.first { app -> app.componentName.className == POLICY_ACTIVITY_CLASS }.isPackageRepresentative)
        assertFalse(actualApps.first { app -> app.componentName.className == SETUP_ACTIVITY_CLASS }.isPackageRepresentative)
    }

    @Test
    fun `keeps every App when a changed package has several launcher activities`() = databaseTest {
        val inputState: ProfileSourceState = baseState()
        val repository: IAppRepositoryImpl = createRepository(state = inputState)
        finishPendingWork()
        inputState.setActivities(
            user = personalUser,
            activities = profileActivities(
                packageName = PERSONAL_PACKAGE,
                user = personalUser,
                activityClasses = listOf(SETUP_ACTIVITY_CLASS, POLICY_ACTIVITY_CLASS)
            )
        )

        inputState.changes.emit(ProfileChange.PackageChanged(user = personalUser, packageName = PERSONAL_PACKAGE))
        val actualApps: List<App> = loadApps(repository = repository).filter { app -> app.profile == personalUser }

        assertEquals(
            listOf(SETUP_ACTIVITY_CLASS, POLICY_ACTIVITY_CLASS),
            actualApps.map { app -> app.componentName.className }
        )
        assertEquals(actualApps.size, actualApps.map { app -> app.key }.distinct().size)
    }

    @Test
    fun `loads Apps from the personal and work Profiles`() = databaseTest {
        val inputState: ProfileSourceState = ProfileSourceState(profiles = listOf(personalProfile, workProfile))
        inputState.setActivities(
            user = personalUser,
            activities = arrayOf(
                profileActivity(packageName = PERSONAL_PACKAGE, user = personalUser),
                profileActivity(packageName = SECOND_PERSONAL_PACKAGE, user = personalUser)
            )
        )
        inputState.setActivities(
            user = workUser,
            activities = arrayOf(profileActivity(packageName = WORK_PACKAGE, user = workUser))
        )
        val repository: IAppRepositoryImpl = createRepository(state = inputState)

        val actualApps: List<App> = loadApps(repository = repository)

        assertEquals(
            listOf(PERSONAL_PACKAGE, SECOND_PERSONAL_PACKAGE, WORK_PACKAGE),
            actualApps.map { app -> app.packageName }
        )
        assertTrue(actualApps.all { app -> app.isPackageRepresentative })
    }

    @Test
    fun `adds a package to the Profile it was installed in`() = databaseTest {
        val inputState: ProfileSourceState = baseState()
        val repository: IAppRepositoryImpl = createRepository(state = inputState)
        finishPendingWork()
        inputState.setActivities(
            user = workUser,
            activities = arrayOf(
                profileActivity(packageName = WORK_PACKAGE, user = workUser),
                profileActivity(packageName = NEW_WORK_PACKAGE, user = workUser)
            )
        )

        inputState.changes.emit(ProfileChange.PackageAdded(user = workUser, packageName = NEW_WORK_PACKAGE))
        val actualApps: List<App> = loadApps(repository = repository)

        assertTrue(actualApps.any { app -> app.packageName == NEW_WORK_PACKAGE && app.profile == workUser })
        assertEquals(2, actualApps.count { app -> app.profile == workUser })
        assertEquals(1, actualApps.count { app -> app.profile == personalUser })
    }

    @Test
    fun `removes a package from the Profile it was uninstalled from`() = databaseTest {
        val inputState: ProfileSourceState = baseState()
        val repository: IAppRepositoryImpl = createRepository(state = inputState)
        finishPendingWork()

        inputState.changes.emit(ProfileChange.PackageRemoved(user = workUser, packageName = WORK_PACKAGE))
        val actualApps: List<App> = loadApps(repository = repository)

        assertFalse(actualApps.any { app -> app.profile == workUser })
        assertTrue(actualApps.any { app -> app.packageName == PERSONAL_PACKAGE && app.profile == personalUser })
    }

    @Test
    fun `updates a package in the Profile it changed in`() = databaseTest {
        val inputState: ProfileSourceState = baseState()
        val repository: IAppRepositoryImpl = createRepository(state = inputState)
        finishPendingWork()
        inputState.setActivities(
            user = workUser,
            activities = arrayOf(profileActivity(packageName = WORK_PACKAGE, user = workUser, label = RENAMED_NAME))
        )

        inputState.changes.emit(ProfileChange.PackageChanged(user = workUser, packageName = WORK_PACKAGE))
        val actualApps: List<App> = loadApps(repository = repository)

        assertEquals(RENAMED_NAME, actualApps.single { app -> app.profile == workUser }.defaultName)
        assertEquals(1, actualApps.count { app -> app.profile == personalUser })
    }

    @Test
    fun `gives one App installed in two Profiles a key per Profile`() = databaseTest {
        val inputState: ProfileSourceState = ProfileSourceState(profiles = listOf(personalProfile, workProfile))
        inputState.setActivities(
            user = personalUser,
            activities = arrayOf(profileActivity(packageName = SHARED_PACKAGE, user = personalUser))
        )
        inputState.setActivities(
            user = workUser,
            activities = arrayOf(profileActivity(packageName = SHARED_PACKAGE, user = workUser))
        )
        val repository: IAppRepositoryImpl = createRepository(state = inputState)

        val actualApps: List<App> = loadApps(repository = repository)

        assertEquals(listOf(personalUser, workUser), actualApps.map { app -> app.profile })
        assertEquals(actualApps.size, actualApps.map { app -> app.key }.distinct().size)
    }

    @Test
    fun `drops a removed Profile's Apps`() = databaseTest {
        val inputState: ProfileSourceState = baseState()
        val repository: IAppRepositoryImpl = createRepository(state = inputState)
        finishPendingWork()
        inputState.profiles.value = listOf(personalProfile)

        inputState.changes.emit(ProfileChange.ProfileRemoved(user = workUser))
        val actualApps: List<App> = loadApps(repository = repository)

        assertEquals(listOf(PERSONAL_PACKAGE), actualApps.map { app -> app.packageName })
    }

    @Test
    fun `does not list Apps from a Locked Private Space`() = databaseTest {
        val inputPrivateProfile: ProfileInfo = createProfileInfo(
            userId = PRIVATE_USER_ID,
            kind = ProfileKind.Private,
            isQuiet = true
        )
        val inputState: ProfileSourceState = ProfileSourceState(profiles = listOf(personalProfile, inputPrivateProfile))
        inputState.setActivities(
            user = personalUser,
            activities = arrayOf(profileActivity(packageName = PERSONAL_PACKAGE, user = personalUser))
        )
        inputState.setActivities(
            user = privateUser,
            activities = arrayOf(profileActivity(packageName = PRIVATE_PACKAGE, user = privateUser))
        )
        val repository: IAppRepositoryImpl = createRepository(state = inputState)

        val actualApps: List<App> = loadApps(repository = repository)

        assertEquals(listOf(PERSONAL_PACKAGE), actualApps.map { app -> app.packageName })
        assertEquals(setOf(personalUser), repository.enumeratedProfiles.value)
    }

    @Test
    fun `enumerates an Unlocked Private Space that lists no Apps`() = databaseTest {
        val inputPrivateProfile: ProfileInfo = createProfileInfo(
            userId = PRIVATE_USER_ID,
            kind = ProfileKind.Private,
            isQuiet = false
        )
        val inputState: ProfileSourceState = ProfileSourceState(profiles = listOf(personalProfile, inputPrivateProfile))
        inputState.setActivities(
            user = personalUser,
            activities = arrayOf(profileActivity(packageName = PERSONAL_PACKAGE, user = personalUser))
        )
        val repository: IAppRepositoryImpl = createRepository(state = inputState)

        val actualApps: List<App> = loadApps(repository = repository)

        assertEquals(listOf(PERSONAL_PACKAGE), actualApps.map { app -> app.packageName })
        assertEquals(setOf(personalUser, privateUser), repository.enumeratedProfiles.value)
    }

    @Test
    fun `lists Apps from an Unlocked Private Space`() = databaseTest {
        val inputPrivateProfile: ProfileInfo = createProfileInfo(
            userId = PRIVATE_USER_ID,
            kind = ProfileKind.Private,
            isQuiet = false
        )
        val inputState: ProfileSourceState = ProfileSourceState(profiles = listOf(personalProfile, inputPrivateProfile))
        inputState.setActivities(
            user = personalUser,
            activities = arrayOf(profileActivity(packageName = PERSONAL_PACKAGE, user = personalUser))
        )
        inputState.setActivities(
            user = privateUser,
            activities = arrayOf(profileActivity(packageName = PRIVATE_PACKAGE, user = privateUser))
        )
        val repository: IAppRepositoryImpl = createRepository(state = inputState)

        val actualApps: List<App> = loadApps(repository = repository)

        val actualPrivateApp: App = actualApps.single { app -> app.profile == privateUser }
        assertEquals(PRIVATE_PACKAGE, actualPrivateApp.packageName)
        assertEquals(ProfileKind.Private, actualPrivateApp.kind)
        assertEquals(listOf(PERSONAL_PACKAGE, PRIVATE_PACKAGE), actualApps.map { app -> app.packageName })
    }

    @Test
    fun `enumerates the Private Space's Apps when it unlocks and hides them when it locks`() = databaseTest {
        val inputPrivateProfile: ProfileInfo = createProfileInfo(
            userId = PRIVATE_USER_ID,
            kind = ProfileKind.Private,
            isQuiet = true
        )
        val inputState: ProfileSourceState = ProfileSourceState(profiles = listOf(personalProfile, inputPrivateProfile))
        inputState.setActivities(
            user = personalUser,
            activities = arrayOf(profileActivity(packageName = PERSONAL_PACKAGE, user = personalUser))
        )
        inputState.setActivities(
            user = privateUser,
            activities = profileActivities(
                packageName = PRIVATE_PACKAGE,
                user = privateUser,
                activityClasses = listOf(SETUP_ACTIVITY_CLASS, POLICY_ACTIVITY_CLASS)
            )
        )
        val repository: IAppRepositoryImpl = createRepository(state = inputState)
        finishPendingWork()
        assertFalse(loadApps(repository = repository).any { app -> app.profile == privateUser })
        assertFalse(privateUser in repository.enumeratedProfiles.value)

        inputState.profiles.value = listOf(personalProfile, inputPrivateProfile.copy(isQuiet = false))
        inputState.changes.emit(ProfileChange.ProfileAvailable(user = privateUser))
        val actualUnlockedPrivateApps: List<App> = loadApps(repository = repository).filter { app -> app.profile == privateUser }
        assertEquals(
            listOf(SETUP_ACTIVITY_CLASS, POLICY_ACTIVITY_CLASS),
            actualUnlockedPrivateApps.map { app -> app.componentName.className }
        )
        assertEquals(actualUnlockedPrivateApps.size, actualUnlockedPrivateApps.map { app -> app.key }.distinct().size)
        assertTrue(privateUser in repository.enumeratedProfiles.value)

        inputState.profiles.value = listOf(personalProfile, inputPrivateProfile)
        inputState.changes.emit(ProfileChange.ProfileUnavailable(user = privateUser))
        assertFalse(loadApps(repository = repository).any { app -> app.profile == privateUser })
        assertFalse(privateUser in repository.enumeratedProfiles.value)

        inputState.profiles.value = listOf(personalProfile, inputPrivateProfile.copy(isQuiet = false))
        inputState.changes.emit(ProfileChange.ProfileAvailable(user = privateUser))
        assertEquals(
            actualUnlockedPrivateApps.map { app -> app.key },
            loadApps(repository = repository).filter { app ->
                app.profile ==
                    privateUser
            }.map { app -> app.key }
        )
    }

    @Test
    fun `adds and removes packages inside the Unlocked Private Space live`() = databaseTest {
        val inputPrivateProfile: ProfileInfo = createProfileInfo(
            userId = PRIVATE_USER_ID,
            kind = ProfileKind.Private,
            isQuiet = false
        )
        val inputState: ProfileSourceState = ProfileSourceState(profiles = listOf(personalProfile, inputPrivateProfile))
        inputState.setActivities(
            user = privateUser,
            activities = arrayOf(profileActivity(packageName = PRIVATE_PACKAGE, user = privateUser))
        )
        val repository: IAppRepositoryImpl = createRepository(state = inputState)
        finishPendingWork()
        inputState.setActivities(
            user = privateUser,
            activities = arrayOf(
                profileActivity(packageName = PRIVATE_PACKAGE, user = privateUser),
                profileActivity(packageName = NEW_PRIVATE_PACKAGE, user = privateUser)
            )
        )

        inputState.changes.emit(ProfileChange.PackageAdded(user = privateUser, packageName = NEW_PRIVATE_PACKAGE))
        assertTrue(loadApps(repository = repository).any { app -> app.packageName == NEW_PRIVATE_PACKAGE })

        inputState.changes.emit(ProfileChange.PackageRemoved(user = privateUser, packageName = NEW_PRIVATE_PACKAGE))
        assertFalse(loadApps(repository = repository).any { app -> app.packageName == NEW_PRIVATE_PACKAGE })
        assertTrue(loadApps(repository = repository).any { app -> app.packageName == PRIVATE_PACKAGE })
    }

    @Test
    fun `resolves existing Favorites and customizations by App key`() = databaseTest {
        val inputState: ProfileSourceState = ProfileSourceState(profiles = listOf(personalProfile, workProfile))
        inputState.setActivities(
            user = workUser,
            activities = arrayOf(profileActivity(packageName = WORK_PACKAGE, user = workUser))
        )
        val appRepository: IAppRepositoryImpl = createRepository(state = inputState)
        finishPendingWork()
        val inputWorkApp: App = appRepository.apps.value.orEmpty().single()
        val repositories: LaunchableRepositories = createLaunchableRepositories(
            appRepository = appRepository,
            profileState = inputState,
            customizations = listOf(LaunchableCustomization(key = inputWorkApp.key, name = RENAMED_NAME))
        )
        database.getFavoriteDao().add(inputWorkApp.key)
        backgroundScope.launch { repositories.launchables.launchableState.collect() }
        backgroundScope.launch { repositories.favorites.favorites.collect() }
        finishPendingWork()

        val actualFavorite: App = repositories.favorites.favorites.first().single() as App

        assertEquals(inputWorkApp.key, actualFavorite.key)
        assertEquals(RENAMED_NAME, actualFavorite.name)
    }

    @Test
    fun `removes a Favorite stored under a package-level key while its current App stays`() = databaseTest {
        val inputState: ProfileSourceState = ProfileSourceState(profiles = listOf(personalProfile))
        inputState.setActivities(
            user = personalUser,
            activities = profileActivities(
                packageName = PERSONAL_PACKAGE,
                user = personalUser,
                activityClasses = listOf(SETUP_ACTIVITY_CLASS, POLICY_ACTIVITY_CLASS)
            )
        )
        val appRepository: IAppRepositoryImpl = createRepository(state = inputState)
        val repositories: LaunchableRepositories = createLaunchableRepositories(appRepository = appRepository, profileState = inputState)
        val inputApp: App = loadApps(repository = appRepository).first { app -> app.componentName.className == POLICY_ACTIVITY_CLASS }
        seedFavorite(key = inputApp.key, position = FIRST_POSITION)
        seedFavorite(key = inputApp.packageLevelKey(), position = SECOND_POSITION)
        backgroundScope.launch { repositories.launchables.launchableState.collect() }
        backgroundScope.launch { repositories.favorites.favorites.collect() }
        finishPendingWork()

        assertEquals(listOf(inputApp.key), favoriteRecords().map { record -> record.key })
        assertEquals(listOf(inputApp), repositories.favorites.favorites.first())
    }

    @Test
    fun `keeps a private Favorite when a package event arrives before the Unlocked Space enumerates`() = databaseTest {
        val inputPrivateProfile: ProfileInfo = createProfileInfo(
            userId = PRIVATE_USER_ID,
            kind = ProfileKind.Private,
            isQuiet = true
        )
        val inputPrivateApp: App = createApp(name = "Secret", packageName = PRIVATE_PACKAGE, profileInfo = inputPrivateProfile)
        val inputOtherPrivateApp: App = createApp(name = "Other", packageName = NEW_PRIVATE_PACKAGE, profileInfo = inputPrivateProfile)
        val inputState: ProfileSourceState = ProfileSourceState(profiles = listOf(personalProfile, inputPrivateProfile))
        inputState.setActivities(
            user = personalUser,
            activities = arrayOf(profileActivity(packageName = PERSONAL_PACKAGE, user = personalUser))
        )
        inputState.setActivities(
            user = privateUser,
            activities = arrayOf(
                profileActivity(packageName = PRIVATE_PACKAGE, user = privateUser),
                profileActivity(packageName = NEW_PRIVATE_PACKAGE, user = privateUser)
            )
        )
        val appRepository: IAppRepositoryImpl = createRepository(state = inputState)
        val repositories: LaunchableRepositories = createLaunchableRepositories(appRepository = appRepository, profileState = inputState)
        seedFavorite(key = inputPrivateApp.key, position = FIRST_POSITION)
        seedFavorite(key = inputOtherPrivateApp.key, position = SECOND_POSITION)
        backgroundScope.launch { repositories.launchables.launchableState.collect() }
        backgroundScope.launch { repositories.favorites.favorites.collect() }
        finishPendingWork()
        assertEquals(2, favoriteRecords().size)

        inputState.profiles.value = listOf(personalProfile, inputPrivateProfile.copy(isQuiet = false))
        finishPendingWork()
        inputState.changes.emit(ProfileChange.PackageAdded(user = privateUser, packageName = PRIVATE_PACKAGE))
        finishPendingWork()

        assertFalse(privateUser in appRepository.enumeratedProfiles.value)
        assertEquals(2, favoriteRecords().size)
    }

    private fun baseState(): ProfileSourceState = ProfileSourceState(profiles = listOf(personalProfile, workProfile)).apply {
        setActivities(
            user = personalUser,
            activities = arrayOf(profileActivity(packageName = PERSONAL_PACKAGE, user = personalUser))
        )
        setActivities(
            user = workUser,
            activities = arrayOf(profileActivity(packageName = WORK_PACKAGE, user = workUser))
        )
    }

    private fun TestScope.createRepository(state: ProfileSourceState): IAppRepositoryImpl = IAppRepositoryImpl(
        context = RuntimeEnvironment.getApplication(),
        profileSource = createProfileSourceMock(state = state),
        scope = backgroundScope,
        dispatcher = databaseDispatcher
    )

    private fun TestScope.loadApps(repository: IAppRepository): List<App> {
        finishPendingWork()
        return repository.apps.value.orEmpty()
    }

    private fun TestScope.createLaunchableRepositories(
        appRepository: IAppRepository,
        profileState: ProfileSourceState,
        customizations: List<LaunchableCustomization> = emptyList()
    ): LaunchableRepositories {
        val mockProfileSource: IProfileSource = createProfileSourceMock(state = profileState)
        val launchableRepository: ILaunchableRepositoryImpl = ILaunchableRepositoryImpl(
            appRepository = appRepository,
            iconRepository = createIconRepositoryMock(inputIcon = Icon.RailIcon(resId = 0)),
            profileSource = mockProfileSource,
            preferencesRepository = createPreferencesRepositoryMock(customizations = customizations),
            folderDao = database.getFolderDao(),
            popupDao = database.getPopupDao(),
            privateSpaceLock = createPrivateSpaceLock(profileSource = mockProfileSource),
            scope = backgroundScope
        )
        return LaunchableRepositories(
            launchables = launchableRepository,
            favorites = createFavoritesRepository(favoriteDao = database.getFavoriteDao(), launchableRepository = launchableRepository)
        )
    }

    private fun ProfileSourceState.setActivities(user: UserHandle, activities: Array<ProfileActivity>) {
        activitiesByUser[user] = activities.toList()
    }

    private fun profileActivities(packageName: String, user: UserHandle, activityClasses: List<String>): Array<ProfileActivity> =
        activityClasses.map { activityClass ->
            profileActivity(packageName = packageName, user = user, label = activityClass, activityClass = activityClass)
        }.toTypedArray()

    private fun profileActivity(
        packageName: String,
        user: UserHandle,
        label: String = packageName,
        badgedLabel: String = label,
        activityClass: String = "$packageName.MainActivity"
    ): ProfileActivity = ProfileActivity(
        componentName = ComponentName(packageName, activityClass),
        user = user,
        label = label,
        badgedLabel = badgedLabel
    )
}

private data class LaunchableRepositories(val launchables: ILaunchableRepositoryImpl, val favorites: IFavoritesRepositoryImpl)
