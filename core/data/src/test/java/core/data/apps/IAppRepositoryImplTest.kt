package core.data.apps

import android.content.ComponentName
import android.os.UserHandle
import core.data.apps.model.App
import core.data.favorites.IFavoritesRepositoryImpl
import core.data.icons.model.Icon
import core.data.launchables.ILaunchableRepositoryImpl
import core.data.prefs.IPreferencesRepository
import core.data.prefs.LaunchableCustomization
import core.data.profiles.ProfileActivity
import core.data.profiles.ProfileChange
import core.data.profiles.ProfileInfo
import core.data.profiles.ProfileKind
import core.data.testing.InMemoryDatabaseTest
import core.data.testing.PERSONAL_USER_ID
import core.data.testing.PRIVATE_USER_ID
import core.data.testing.ProfileSourceState
import core.data.testing.WORK_USER_ID
import core.data.testing.createFavoritesRepository
import core.data.testing.createIconRepositoryMock
import core.data.testing.createPreferencesRepositoryMock
import core.data.testing.createProfileSourceMock
import core.data.testing.createUserHandle
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
private const val RENAMED_NAME: String = "Renamed"

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
    fun `does not list Apps from a Private Profile`() = databaseTest {
        val inputPrivateProfile: ProfileInfo = ProfileInfo(
            user = privateUser,
            serial = PRIVATE_USER_ID.toLong(),
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
            activities = arrayOf(profileActivity(packageName = "com.example.private", user = privateUser))
        )
        val repository: IAppRepositoryImpl = createRepository(state = inputState)

        val actualApps: List<App> = loadApps(repository = repository)

        assertEquals(listOf(PERSONAL_PACKAGE), actualApps.map { app -> app.packageName })
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
        val mockPreferencesRepository: IPreferencesRepository = createPreferencesRepositoryMock(
            customizations = listOf(LaunchableCustomization(key = inputWorkApp.key, name = RENAMED_NAME))
        )
        val launchableRepository: ILaunchableRepositoryImpl = ILaunchableRepositoryImpl(
            appRepository = appRepository,
            iconRepository = createIconRepositoryMock(inputIcon = Icon.ActivityIcon(inputWorkApp.componentName)),
            preferencesRepository = mockPreferencesRepository,
            folderDao = database.getFolderDao(),
            popupDao = database.getPopupDao(),
            scope = backgroundScope
        )
        database.getFavoriteDao().add(inputWorkApp.key)
        val favoritesRepository: IFavoritesRepositoryImpl = createFavoritesRepository(
            favoriteDao = database.getFavoriteDao(),
            launchableRepository = launchableRepository
        )
        backgroundScope.launch { launchableRepository.launchableState.collect() }
        backgroundScope.launch { favoritesRepository.favorites.collect() }
        finishPendingWork()

        val actualFavorite: App = favoritesRepository.favorites.first().single() as App

        assertEquals(inputWorkApp.key, actualFavorite.key)
        assertEquals(RENAMED_NAME, actualFavorite.name)
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

    private fun ProfileSourceState.setActivities(user: UserHandle, activities: Array<ProfileActivity>) {
        activitiesByUser[user] = activities.toList()
    }

    private fun profileActivity(packageName: String, user: UserHandle, label: String = packageName): ProfileActivity = ProfileActivity(
        componentName = ComponentName(packageName, "$packageName.MainActivity"),
        user = user,
        label = label
    )
}
