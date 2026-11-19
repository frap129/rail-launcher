package core.data.apps

import android.content.ComponentName
import android.os.UserHandle
import app.cash.turbine.test
import core.data.apps.model.App
import core.data.favorites.IFavoritesRepositoryImpl
import core.data.icons.model.Icon
import core.data.launchables.ILaunchableRepositoryImpl
import core.data.prefs.LaunchableCustomization
import core.data.profiles.IPrivateSpaceLock
import core.data.profiles.IPrivateSpaceStatus
import core.data.profiles.IProfileSource
import core.data.profiles.ProfileActivity
import core.data.profiles.ProfileChange
import core.data.profiles.ProfileInfo
import core.data.profiles.ProfileKind
import core.data.profiles.isPrivate
import core.data.room.LastKnownAppDatabase
import core.data.testing.FIRST_POSITION
import core.data.testing.InMemoryDatabaseTest
import core.data.testing.MISSING_WORK_USER_ID
import core.data.testing.PERSONAL_USER_ID
import core.data.testing.PRIVATE_USER_ID
import core.data.testing.ProfileSourceState
import core.data.testing.SECOND_POSITION
import core.data.testing.TEST_NOW_EPOCH_MS
import core.data.testing.TimberLogRecorder
import core.data.testing.WORK_USER_ID
import core.data.testing.createApp
import core.data.testing.createClockReading
import core.data.testing.createClockSourceMock
import core.data.testing.createFavoritesRepository
import core.data.testing.createIconRepositoryMock
import core.data.testing.createInMemoryDatabase
import core.data.testing.createLaunchCountRepository
import core.data.testing.createPreferencesRepositoryMock
import core.data.testing.createPrivateSpaceLock
import core.data.testing.createPrivateSpaceStatus
import core.data.testing.createProfileInfo
import core.data.testing.createProfileSourceMock
import core.data.testing.createShortcutSourceMock
import core.data.testing.createStandaloneShortcutSource
import core.data.testing.createUserHandle
import core.data.testing.packageLevelKey
import core.data.time.IClockSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.robolectric.RuntimeEnvironment

private const val PERSONAL_PACKAGE: String = "com.example.personal"
private const val SECOND_PERSONAL_PACKAGE: String = "com.example.second"
private const val WORK_PACKAGE: String = "com.example.work"
private const val NEW_WORK_PACKAGE: String = "com.example.new.work"
private const val ADDED_PROFILE_PACKAGE: String = "com.example.added.profile"
private const val SHARED_PACKAGE: String = "com.example.shared"
private const val PRIVATE_PACKAGE: String = "com.example.private"
private const val NEW_PRIVATE_PACKAGE: String = "com.example.new.private"
private const val RENAMED_NAME: String = "Renamed"
private const val POLICY_ACTIVITY_CLASS: String = "PolicyManagementActivity"
private const val SETUP_ACTIVITY_CLASS: String = "SetupManagementLaunchActivity"

@OptIn(ExperimentalCoroutinesApi::class)
class IAppRepositoryImplTest : InMemoryDatabaseTest() {
    private lateinit var privateSpaceStatus: IPrivateSpaceStatus
    private lateinit var privateSpaceLock: IPrivateSpaceLock
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
    private lateinit var lastKnownAppDatabase: LastKnownAppDatabase

    @Before
    fun createLastKnownAppDatabase() {
        lastKnownAppDatabase = createInMemoryDatabase(
            context = RuntimeEnvironment.getApplication(),
            queryContext = databaseDispatcher
        )
    }

    @After
    fun closeLastKnownAppDatabase() {
        lastKnownAppDatabase.close()
    }

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
    fun `publishes Apps only after the Profiles have loaded`() = databaseTest {
        val inputPrivateProfile: ProfileInfo = createProfileInfo(
            userId = PRIVATE_USER_ID,
            kind = ProfileKind.Private
        )
        val inputState: ProfileSourceState = baseState().apply {
            profiles.value = listOf(personalProfile, workProfile, inputPrivateProfile)
            isLoaded.value = false
            setActivities(
                user = privateUser,
                activities = arrayOf(profileActivity(packageName = PRIVATE_PACKAGE, user = privateUser))
            )
        }
        val repository: IAppRepositoryImpl = createRepository(state = inputState)
        finishPendingWork()

        assertNull(repository.homeApps.first())

        inputState.isLoaded.value = true
        finishPendingWork()

        assertEquals(
            setOf(personalUser, workUser, privateUser),
            repository.homeAppsOf<HomeApps.Live>().apps.mapTo(mutableSetOf()) { app -> app.profile }
        )
    }

    @Test
    fun `carries each App's first install and last update times`() = databaseTest {
        val inputFirstInstallTime: Long = TEST_NOW_EPOCH_MS
        val inputLastUpdateTime: Long = TEST_NOW_EPOCH_MS + 1L
        val inputState: ProfileSourceState = ProfileSourceState(profiles = listOf(personalProfile)).apply {
            setActivities(
                user = personalUser,
                activities = arrayOf(
                    profileActivity(
                        packageName = PERSONAL_PACKAGE,
                        user = personalUser,
                        firstInstallTime = inputFirstInstallTime,
                        lastUpdateTime = inputLastUpdateTime
                    )
                )
            )
        }
        val repository: IAppRepositoryImpl = createRepository(state = inputState)

        val actualApp: App = loadApps(repository = repository).single()

        assertEquals(inputFirstInstallTime, actualApp.firstInstallTime)
        assertEquals(inputLastUpdateTime, actualApp.lastUpdateTime)
    }

    @Test
    fun `carries each App's removability`() = databaseTest {
        val inputState: ProfileSourceState = ProfileSourceState(profiles = listOf(personalProfile)).apply {
            setActivities(
                user = personalUser,
                activities = arrayOf(
                    profileActivity(packageName = PERSONAL_PACKAGE, user = personalUser, isRemovable = false)
                )
            )
        }
        val repository: IAppRepositoryImpl = createRepository(state = inputState)

        val actualApp: App = loadApps(repository = repository).single()

        assertFalse(actualApp.isRemovable)
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
    fun `carries each App's package suspension state`() = databaseTest {
        val inputState: ProfileSourceState = ProfileSourceState(profiles = listOf(personalProfile)).apply {
            setActivities(
                user = personalUser,
                activities = arrayOf(
                    profileActivity(packageName = PERSONAL_PACKAGE, user = personalUser, isSuspended = true),
                    profileActivity(packageName = SECOND_PERSONAL_PACKAGE, user = personalUser)
                )
            )
        }
        val repository: IAppRepositoryImpl = createRepository(state = inputState)

        val actualApps: List<App> = loadApps(repository = repository)

        assertEquals(
            mapOf(PERSONAL_PACKAGE to true, SECOND_PERSONAL_PACKAGE to false),
            actualApps.associate { app -> app.packageName to app.isSuspended }
        )
    }

    @Test
    fun `marks suspended Apps and clears the mark when they are unsuspended`() = databaseTest {
        val inputState: ProfileSourceState = baseState()
        val repository: IAppRepositoryImpl = createRepository(state = inputState)
        finishPendingWork()

        inputState.changes.emit(
            ProfileChange.PackagesSuspended(user = workUser, packageNames = listOf(WORK_PACKAGE), isSuspended = true)
        )
        assertTrue(loadApps(repository = repository).single { app -> app.profile == workUser }.isSuspended)

        inputState.changes.emit(
            ProfileChange.PackagesSuspended(user = workUser, packageNames = listOf(WORK_PACKAGE), isSuspended = false)
        )
        assertFalse(loadApps(repository = repository).single { app -> app.profile == workUser }.isSuspended)
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
        assertEquals(setOf(personalUser), privateSpaceStatus.listedProfiles.value)
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
        assertEquals(setOf(personalUser, privateUser), privateSpaceStatus.listedProfiles.value)
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
        assertFalse(privateUser in privateSpaceStatus.listedProfiles.value)

        inputState.profiles.value = listOf(personalProfile, inputPrivateProfile.copy(isQuiet = false))
        inputState.changes.emit(ProfileChange.ProfileAvailable(user = privateUser))
        val actualUnlockedPrivateApps: List<App> = loadApps(repository = repository).filter { app -> app.profile == privateUser }
        assertEquals(
            listOf(SETUP_ACTIVITY_CLASS, POLICY_ACTIVITY_CLASS),
            actualUnlockedPrivateApps.map { app -> app.componentName.className }
        )
        assertEquals(actualUnlockedPrivateApps.size, actualUnlockedPrivateApps.map { app -> app.key }.distinct().size)
        assertTrue(privateUser in privateSpaceStatus.listedProfiles.value)

        inputState.profiles.value = listOf(personalProfile, inputPrivateProfile)
        inputState.changes.emit(ProfileChange.ProfileUnavailable(user = privateUser))
        assertFalse(loadApps(repository = repository).any { app -> app.profile == privateUser })
        assertFalse(privateUser in privateSpaceStatus.listedProfiles.value)

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
    fun `keeps a package's private Apps and the Private Space listed while a Lock is requested`() = databaseTest {
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
        assertTrue(privateUser in privateSpaceStatus.listedProfiles.value)

        privateSpaceLock.requestLock()
        finishPendingWork()

        inputState.setActivities(
            user = privateUser,
            activities = arrayOf(profileActivity(packageName = PRIVATE_PACKAGE, user = privateUser, label = RENAMED_NAME))
        )
        inputState.changes.emit(ProfileChange.PackageChanged(user = privateUser, packageName = PRIVATE_PACKAGE))

        val actualApps: List<App> = loadApps(repository = repository)
        assertEquals(RENAMED_NAME, actualApps.single { app -> app.profile == privateUser }.defaultName)
        assertTrue(privateUser in privateSpaceStatus.listedProfiles.value)
        assertTrue(privateSpaceStatus.status.value.areKeysKept)

        privateSpaceLock.clearLockRequest()
        finishPendingWork()

        assertFalse(privateSpaceStatus.status.value.areKeysKept)
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
        val inputWorkApp: App = appRepository.homeAppsOf<HomeApps.Live>().apps.single()
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

        assertFalse(privateUser in privateSpaceStatus.listedProfiles.value)
        assertEquals(2, favoriteRecords().size)
    }

    @Test
    fun `saves the live App list after it loads`() = databaseTest {
        val inputLastUpdateTime: Long = TEST_NOW_EPOCH_MS + 1L
        val inputState: ProfileSourceState = baseState().apply {
            setActivities(
                user = personalUser,
                activities = arrayOf(
                    profileActivity(
                        packageName = PERSONAL_PACKAGE,
                        user = personalUser,
                        label = "Personal",
                        badgedLabel = "Personal badge",
                        firstInstallTime = TEST_NOW_EPOCH_MS,
                        lastUpdateTime = inputLastUpdateTime,
                        isRemovable = false,
                        isSystemApp = true,
                        isSuspended = true
                    )
                )
            )
            setActivities(
                user = workUser,
                activities = arrayOf(
                    profileActivity(packageName = WORK_PACKAGE, user = workUser, label = "Work", badgedLabel = "Work badge")
                )
            )
        }
        val repository: IAppRepositoryImpl = createRepository(state = inputState)

        val actualApps: List<App> = loadApps(repository = repository)
        val actualRecords: List<LastKnownAppRecord> = lastKnownAppRecords()

        val expectedRecords: Set<LastKnownAppRecord> = setOf(
            LastKnownAppRecord(
                componentName = ComponentName(PERSONAL_PACKAGE, "$PERSONAL_PACKAGE.MainActivity"),
                profileSerial = PERSONAL_USER_ID.toLong(),
                label = "Personal",
                badgedLabel = "Personal badge",
                isPackageRepresentative = true,
                firstInstallTime = TEST_NOW_EPOCH_MS,
                lastUpdateTime = inputLastUpdateTime,
                isRemovable = false,
                isSystemApp = true,
                isSuspended = true
            ),
            LastKnownAppRecord(
                componentName = ComponentName(WORK_PACKAGE, "$WORK_PACKAGE.MainActivity"),
                profileSerial = WORK_USER_ID.toLong(),
                label = "Work",
                badgedLabel = "Work badge",
                isPackageRepresentative = true,
                firstInstallTime = 0L,
                lastUpdateTime = 0L,
                isRemovable = true,
                isSystemApp = false,
                isSuspended = false
            )
        )
        assertEquals(expectedRecords, actualRecords.toSet())
        assertEquals(actualApps.size, actualRecords.size)
    }

    @Test
    fun `never saves Private Space Apps whether the Space is locked or unlocked`() = databaseTest {
        val inputLockedPrivateProfile: ProfileInfo = createProfileInfo(
            userId = PRIVATE_USER_ID,
            kind = ProfileKind.Private,
            isQuiet = true
        )
        val inputState: ProfileSourceState = ProfileSourceState(profiles = listOf(personalProfile, inputLockedPrivateProfile))
        inputState.setActivities(
            user = personalUser,
            activities = arrayOf(profileActivity(packageName = PERSONAL_PACKAGE, user = personalUser))
        )
        inputState.setActivities(
            user = privateUser,
            activities = arrayOf(profileActivity(packageName = PRIVATE_PACKAGE, user = privateUser))
        )
        val repository: IAppRepositoryImpl = createRepository(state = inputState)
        finishPendingWork()
        assertFalse(loadApps(repository = repository).any { app -> app.profile == privateUser })
        assertFalse(lastKnownAppRecords().any { record -> record.profileSerial == PRIVATE_USER_ID.toLong() })

        inputState.profiles.value = listOf(personalProfile, inputLockedPrivateProfile.copy(isQuiet = false))
        inputState.changes.emit(ProfileChange.ProfileAvailable(user = privateUser))
        val actualUnlockedApps: List<App> = loadApps(repository = repository)
        assertTrue(actualUnlockedApps.any { app -> app.profile == privateUser })
        assertFalse(lastKnownAppRecords().any { record -> record.profileSerial == PRIVATE_USER_ID.toLong() })

        inputState.profiles.value = listOf(personalProfile, inputLockedPrivateProfile)
        inputState.changes.emit(ProfileChange.ProfileUnavailable(user = privateUser))
        finishPendingWork()
        assertFalse(lastKnownAppRecords().any { record -> record.profileSerial == PRIVATE_USER_ID.toLong() })
    }

    @Test
    fun `never saves Apps from an Other Profile`() = databaseTest {
        val inputOtherUser: UserHandle = createUserHandle(userId = MISSING_WORK_USER_ID)
        val inputOtherProfile: ProfileInfo = createProfileInfo(userId = MISSING_WORK_USER_ID, kind = ProfileKind.Other)
        val inputState: ProfileSourceState = ProfileSourceState(profiles = listOf(personalProfile, inputOtherProfile))
        inputState.setActivities(
            user = personalUser,
            activities = arrayOf(profileActivity(packageName = PERSONAL_PACKAGE, user = personalUser))
        )
        inputState.setActivities(
            user = inputOtherUser,
            activities = arrayOf(profileActivity(packageName = ADDED_PROFILE_PACKAGE, user = inputOtherUser))
        )
        val repository: IAppRepositoryImpl = createRepository(state = inputState)

        val actualApps: List<App> = loadApps(repository = repository)

        assertTrue(actualApps.any { app -> app.profile == inputOtherUser })
        assertFalse(lastKnownAppRecords().any { record -> record.profileSerial == inputOtherProfile.serial })
    }

    @Test
    fun `rewrites the Last-known App list after every live change`() = databaseTest {
        val inputState: ProfileSourceState = baseState()
        val repository: IAppRepositoryImpl = createRepository(state = inputState)
        finishPendingWork()
        assertLastKnownAppListMatchesLive(repository = repository)

        inputState.setActivities(
            user = workUser,
            activities = arrayOf(
                profileActivity(packageName = WORK_PACKAGE, user = workUser),
                profileActivity(packageName = NEW_WORK_PACKAGE, user = workUser)
            )
        )
        inputState.changes.emit(ProfileChange.PackageAdded(user = workUser, packageName = NEW_WORK_PACKAGE))
        finishPendingWork()
        assertLastKnownAppListMatchesLive(repository = repository)

        inputState.setActivities(
            user = workUser,
            activities = arrayOf(
                profileActivity(packageName = WORK_PACKAGE, user = workUser, label = RENAMED_NAME),
                profileActivity(packageName = NEW_WORK_PACKAGE, user = workUser)
            )
        )
        inputState.changes.emit(ProfileChange.PackageChanged(user = workUser, packageName = WORK_PACKAGE))
        finishPendingWork()
        assertLastKnownAppListMatchesLive(repository = repository)
        assertTrue(lastKnownAppRecords().any { record -> record.label == RENAMED_NAME })

        inputState.setActivities(
            user = workUser,
            activities = arrayOf(profileActivity(packageName = WORK_PACKAGE, user = workUser))
        )
        inputState.changes.emit(ProfileChange.PackageRemoved(user = workUser, packageName = NEW_WORK_PACKAGE))
        finishPendingWork()
        assertLastKnownAppListMatchesLive(repository = repository)

        val inputAddedUser: UserHandle = createUserHandle(userId = MISSING_WORK_USER_ID)
        val inputAddedProfile: ProfileInfo = createProfileInfo(userId = MISSING_WORK_USER_ID, kind = ProfileKind.Work)
        inputState.usersBySerial[inputAddedProfile.serial] = inputAddedUser
        inputState.serialsByUser[inputAddedUser] = inputAddedProfile.serial
        inputState.profiles.value = inputState.profiles.value + inputAddedProfile
        inputState.setActivities(
            user = inputAddedUser,
            activities = arrayOf(profileActivity(packageName = ADDED_PROFILE_PACKAGE, user = inputAddedUser))
        )
        inputState.changes.emit(ProfileChange.ProfileAdded(user = inputAddedUser))
        finishPendingWork()
        assertLastKnownAppListMatchesLive(repository = repository)

        inputState.profiles.value = inputState.profiles.value - inputAddedProfile
        inputState.changes.emit(ProfileChange.ProfileRemoved(user = inputAddedUser))
        finishPendingWork()
        assertLastKnownAppListMatchesLive(repository = repository)
    }

    @Test
    fun `publishes the live App list before saving finishes`() = databaseTest {
        val saveGate: CompletableDeferred<Unit> = CompletableDeferred()
        val mockLastKnownAppStore: ILastKnownAppStore = createLastKnownAppStoreMock(save = { saveGate.await() })
        val repository: IAppRepositoryImpl = createRepository(
            state = baseState(),
            lastKnownAppStore = mockLastKnownAppStore
        )

        finishPendingWork()

        assertEquals(2, repository.homeAppsOf<HomeApps.Live>().apps.size)
        coVerify(exactly = 1) { mockLastKnownAppStore.save(any()) }

        saveGate.complete(Unit)
        finishPendingWork()
    }

    @Test
    fun `publishes the live App list when saving fails`() = databaseTest {
        val inputState: ProfileSourceState = baseState()
        val mockLastKnownAppStore: ILastKnownAppStore = createLastKnownAppStoreMock(
            save = { throw IllegalStateException("Last-known App list write failed") }
        )
        val logRecorder: TimberLogRecorder = TimberLogRecorder()
        val repository: IAppRepositoryImpl = createRepository(
            state = inputState,
            lastKnownAppStore = mockLastKnownAppStore
        )

        logRecorder.recordSuspending {
            finishPendingWork()
            assertEquals(2, repository.homeAppsOf<HomeApps.Live>().apps.size)

            inputState.setActivities(
                user = workUser,
                activities = arrayOf(
                    profileActivity(packageName = WORK_PACKAGE, user = workUser),
                    profileActivity(packageName = NEW_WORK_PACKAGE, user = workUser)
                )
            )
            inputState.changes.emit(ProfileChange.PackageAdded(user = workUser, packageName = NEW_WORK_PACKAGE))
            finishPendingWork()
        }

        assertEquals(3, repository.homeAppsOf<HomeApps.Live>().apps.size)
        coVerify(exactly = 2) { mockLastKnownAppStore.save(any()) }
        logRecorder.assertLoggedWithout()
    }

    @Test
    fun `publishes the Last-known App list as Home's Apps before the live load finishes`() = databaseTest {
        val inputState: ProfileSourceState = ProfileSourceState(profiles = listOf(personalProfile), isLoaded = false)
        lastKnownAppDatabase.getLastKnownAppDao().insertAll(listOf(lastKnownRecord(profile = personalProfile)))
        val repository: IAppRepositoryImpl = createRepository(state = inputState)

        finishPendingWork()

        val actualHomeApps: HomeApps.LastKnown = repository.homeAppsOf()
        assertEquals(listOf("Last-known"), actualHomeApps.apps.map { app -> app.defaultName })
    }

    @Test
    fun `treats an empty Last-known App list as no Last-known Apps`() = databaseTest {
        val inputState: ProfileSourceState = ProfileSourceState(profiles = listOf(personalProfile), isLoaded = false)
        val repository: IAppRepositoryImpl = createRepository(state = inputState)

        finishPendingWork()

        assertNull(repository.homeApps.first())
    }

    @Test
    fun `drops Last-known Apps whose Profile no longer exists`() = databaseTest {
        val inputProfiles: List<ProfileInfo> = listOf(personalProfile)
        val inputState: ProfileSourceState = ProfileSourceState(profiles = inputProfiles, isLoaded = false)
        lastKnownAppDatabase.getLastKnownAppDao().insertAll(
            listOf(
                lastKnownRecord(profile = personalProfile),
                lastKnownRecord(profile = workProfile, packageName = WORK_PACKAGE)
            )
        )
        val repository: IAppRepositoryImpl = createRepository(state = inputState)

        finishPendingWork()

        val actualHomeApps: HomeApps.LastKnown = repository.homeAppsOf()
        assertEquals(
            listOf(ComponentName(PERSONAL_PACKAGE, "$PERSONAL_PACKAGE.MainActivity")),
            actualHomeApps.apps.map { app -> app.componentName }
        )
    }

    @Test
    fun `never publishes a Private Space App from the Last-known App list`() = databaseTest {
        val inputPrivateProfile: ProfileInfo = createProfileInfo(
            userId = PRIVATE_USER_ID,
            kind = ProfileKind.Private,
            isQuiet = false
        )
        val inputState: ProfileSourceState = ProfileSourceState(
            profiles = listOf(personalProfile, inputPrivateProfile),
            isLoaded = false
        )
        lastKnownAppDatabase.getLastKnownAppDao().insertAll(
            listOf(
                lastKnownRecord(profile = personalProfile),
                lastKnownRecord(profile = inputPrivateProfile, packageName = PRIVATE_PACKAGE)
            )
        )
        val repository: IAppRepositoryImpl = createRepository(state = inputState)

        finishPendingWork()

        val actualHomeApps: HomeApps.LastKnown = repository.homeAppsOf()
        assertTrue(actualHomeApps.apps.none { app -> app.profileInfo.isPrivate })
        assertEquals(1, actualHomeApps.apps.size)
    }

    @Test
    fun `publishes the live App load without waiting for the Last-known App list read`() = databaseTest {
        val inputState: ProfileSourceState = baseState()
        val readGate: CompletableDeferred<Unit> = CompletableDeferred()
        val mockLastKnownAppStore: ILastKnownAppStore = createLastKnownAppStoreMock(
            read = {
                readGate.await()
                emptyList()
            }
        )
        val repository: IAppRepositoryImpl = createRepository(
            state = inputState,
            lastKnownAppStore = mockLastKnownAppStore
        )

        finishPendingWork()

        val actualHomeApps: HomeApps.Live = repository.homeAppsOf()
        assertEquals(2, actualHomeApps.apps.size)

        readGate.complete(Unit)
        finishPendingWork()

        assertTrue(repository.homeApps.first() is HomeApps.Live)
    }

    @Test
    fun `ignores the Last-known App list when its read finishes after the live load`() = databaseTest {
        val inputState: ProfileSourceState = baseState()
        val readGate: CompletableDeferred<Unit> = CompletableDeferred()
        val mockLastKnownAppStore: ILastKnownAppStore = createLastKnownAppStoreMock(
            read = {
                readGate.await()
                listOf(createApp(name = "Late", packageName = PERSONAL_PACKAGE).toRecord())
            }
        )
        val repository: IAppRepositoryImpl = createRepository(
            state = inputState,
            lastKnownAppStore = mockLastKnownAppStore
        )
        finishPendingWork()
        assertEquals(2, repository.homeAppsOf<HomeApps.Live>().apps.size)

        repository.homeApps.test {
            assertTrue(awaitItem() is HomeApps.Live)

            readGate.complete(Unit)
            finishPendingWork()

            expectNoEvents()
        }
    }

    @Test
    fun `publishes the live App load when reading the Last-known App list fails`() = databaseTest {
        val inputState: ProfileSourceState = baseState()
        val mockLastKnownAppStore: ILastKnownAppStore = createLastKnownAppStoreMock(
            read = { throw IllegalStateException("Last-known App list read failed") }
        )
        val logRecorder: TimberLogRecorder = TimberLogRecorder()
        val repository: IAppRepositoryImpl = createRepository(
            state = inputState,
            lastKnownAppStore = mockLastKnownAppStore
        )

        logRecorder.recordSuspending { finishPendingWork() }

        assertEquals(2, repository.homeAppsOf<HomeApps.Live>().apps.size)
        logRecorder.assertLoggedWithout()
    }

    private fun createLastKnownAppStoreMock(
        read: suspend () -> List<LastKnownAppRecord> = { emptyList() },
        save: suspend (List<App>) -> Unit = {}
    ): ILastKnownAppStore {
        val mockLastKnownAppStore: ILastKnownAppStore = mockk()
        coEvery { mockLastKnownAppStore.read() } coAnswers { read() }
        coEvery { mockLastKnownAppStore.save(any()) } coAnswers { save(firstArg()) }
        return mockLastKnownAppStore
    }

    private fun lastKnownRecord(
        profile: ProfileInfo,
        packageName: String = PERSONAL_PACKAGE,
        label: String = "Last-known"
    ): LastKnownAppRecord = createApp(name = label, packageName = packageName, profileInfo = profile).toRecord()

    private suspend inline fun <reified T : HomeApps> IAppRepository.homeAppsOf(): T =
        homeApps.first() as? T ?: throw AssertionError("Expected Home Apps of type ${T::class.simpleName}")

    private suspend fun lastKnownAppRecords(): List<LastKnownAppRecord> = lastKnownAppDatabase.getLastKnownAppDao().getAll()

    private suspend fun assertLastKnownAppListMatchesLive(repository: IAppRepository) {
        val expectedKeys: Set<Pair<ComponentName, Long>> = repository.homeAppsOf<HomeApps.Live>().apps
            .filterNot { app -> app.profileInfo.isPrivate }
            .map { app -> app.componentName to app.profileInfo.serial }
            .toSet()
        val actualKeys: Set<Pair<ComponentName, Long>> = lastKnownAppRecords()
            .map { record -> record.componentName to record.profileSerial }
            .toSet()
        assertEquals(expectedKeys, actualKeys)
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

    private fun TestScope.createRepository(
        state: ProfileSourceState,
        lastKnownAppStore: ILastKnownAppStore = ILastKnownAppStoreImpl(lastKnownAppDao = lastKnownAppDatabase.getLastKnownAppDao())
    ): IAppRepositoryImpl {
        val mockProfileSource: IProfileSource = createProfileSourceMock(state = state)
        privateSpaceLock = createPrivateSpaceLock(profileSource = mockProfileSource)
        privateSpaceStatus = createPrivateSpaceStatus(
            profileSource = mockProfileSource,
            privateSpaceLock = privateSpaceLock
        )
        return IAppRepositoryImpl(
            context = RuntimeEnvironment.getApplication(),
            profileSource = mockProfileSource,
            privateSpaceStatus = privateSpaceStatus,
            lastKnownAppStore = lastKnownAppStore,
            scope = backgroundScope,
            dispatcher = databaseDispatcher
        )
    }

    private suspend fun TestScope.loadApps(repository: IAppRepository): List<App> {
        finishPendingWork()
        return repository.homeAppsOf<HomeApps.Live>().apps
    }

    private fun TestScope.createLaunchableRepositories(
        appRepository: IAppRepository,
        profileState: ProfileSourceState,
        customizations: List<LaunchableCustomization> = emptyList()
    ): LaunchableRepositories {
        val mockProfileSource: IProfileSource = createProfileSourceMock(state = profileState)
        val mockClockSource: IClockSource = createClockSourceMock(
            reading = createClockReading(instant = Instant.ofEpochMilli(0L))
        )
        val launchableRepository: ILaunchableRepositoryImpl = ILaunchableRepositoryImpl(
            appRepository = appRepository,
            iconRepository = createIconRepositoryMock(inputIcon = Icon.RailIcon(resId = 0)),
            profileSource = mockProfileSource,
            preferencesRepository = createPreferencesRepositoryMock(customizations = customizations),
            folderDao = database.getFolderDao(),
            popupDao = database.getPopupDao(),
            standaloneShortcutSource = createStandaloneShortcutSource(
                standaloneShortcutDao = database.getStandaloneShortcutDao(),
                shortcutSource = createShortcutSourceMock()
            ),
            privateSpaceStatus = privateSpaceStatus,
            launchCountRepository = createLaunchCountRepository(
                launchCountDao = database.getLaunchCountDao(),
                clockSource = mockClockSource,
                dispatcher = databaseDispatcher
            ),
            clockSource = mockClockSource,
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
        activityClass: String = "$packageName.MainActivity",
        firstInstallTime: Long = 0,
        lastUpdateTime: Long = 0,
        isRemovable: Boolean = true,
        isSystemApp: Boolean = false,
        isSuspended: Boolean = false
    ): ProfileActivity = ProfileActivity(
        componentName = ComponentName(packageName, activityClass),
        user = user,
        label = label,
        badgedLabel = badgedLabel,
        firstInstallTime = firstInstallTime,
        lastUpdateTime = lastUpdateTime,
        isRemovable = isRemovable,
        isSystemApp = isSystemApp,
        isSuspended = isSuspended
    )
}

private data class LaunchableRepositories(val launchables: ILaunchableRepositoryImpl, val favorites: IFavoritesRepositoryImpl)
