package core.data.icons

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import android.os.UserHandle
import core.data.icons.model.Icon
import core.data.icons.model.IconPack
import core.data.profiles.ProfileChange
import core.data.profiles.ProfileInfo
import core.data.profiles.ProfileKind
import core.data.testing.InMemoryDatabaseTest
import core.data.testing.PERSONAL_USER_ID
import core.data.testing.PRIVATE_USER_ID
import core.data.testing.ProfileSourceState
import core.data.testing.WORK_USER_ID
import core.data.testing.createProfileInfo
import core.data.testing.createProfileSourceMock
import core.data.testing.createUserHandle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

private const val ICON_PACK_PACKAGE_NAME: String = "com.example.iconpack"
private const val ICON_PACK_LABEL: String = "Example icon pack"
private const val APP_PACKAGE_NAME: String = "com.example.app"
private const val APP_ACTIVITY_NAME: String = "com.example.app.MainActivity"
private const val PACK_ICON_RES_ID: Int = 42

@OptIn(ExperimentalCoroutinesApi::class)
class IIconRepositoryImplTest : InMemoryDatabaseTest() {
    private val inputPersonalUser: UserHandle = createUserHandle(userId = PERSONAL_USER_ID)
    private val inputWorkUser: UserHandle = createUserHandle(userId = WORK_USER_ID)
    private val inputPrivateUser: UserHandle = createUserHandle(userId = PRIVATE_USER_ID)
    private val inputComponentName: ComponentName = ComponentName(APP_PACKAGE_NAME, APP_ACTIVITY_NAME)

    @Test
    fun `leaves a personal Profile's icon unbadged`() = databaseTest {
        val inputProfile: ProfileInfo = createProfileInfo(userId = PERSONAL_USER_ID)
        val inputState: ProfileSourceState = ProfileSourceState(profiles = listOf(inputProfile))
        val repository: IIconRepositoryImpl = createRepository(state = inputState)

        val actualIcon: Icon = repository.getIcon(
            packPackageName = ICON_PACK_PACKAGE_NAME,
            componentName = inputComponentName,
            profile = inputProfile
        )

        assertEquals(Icon.ActivityIcon(inputComponentName), actualIcon)
    }

    @Test
    fun `badges a non-personal Profile's icon`() = databaseTest {
        val inputProfile: ProfileInfo = createProfileInfo(userId = WORK_USER_ID, kind = ProfileKind.Work)
        val inputState: ProfileSourceState = ProfileSourceState(profiles = listOf(inputProfile))
        val repository: IIconRepositoryImpl = createRepository(state = inputState)

        val actualIcon: Icon = repository.getIcon(
            packPackageName = ICON_PACK_PACKAGE_NAME,
            componentName = inputComponentName,
            profile = inputProfile
        )

        assertEquals(Icon.ProfileIcon(icon = Icon.ActivityIcon(inputComponentName), profile = inputWorkUser), actualIcon)
    }

    @Test
    fun `falls back to a Private Space Profile's activity icon`() = databaseTest {
        val inputProfile: ProfileInfo = createProfileInfo(userId = PRIVATE_USER_ID, kind = ProfileKind.Private)
        val inputState: ProfileSourceState = ProfileSourceState(profiles = listOf(inputProfile))
        val repository: IIconRepositoryImpl = createRepository(state = inputState)

        val actualIcon: Icon = repository.getIcon(
            packPackageName = ICON_PACK_PACKAGE_NAME,
            componentName = inputComponentName,
            profile = inputProfile
        )

        assertEquals(Icon.ProfileIcon(icon = Icon.ActivityIcon(inputComponentName), profile = inputPrivateUser), actualIcon)
    }

    @Test
    fun `wraps a Private Space Profile's pack icon`() = databaseTest {
        val inputProfile: ProfileInfo = createProfileInfo(userId = PRIVATE_USER_ID, kind = ProfileKind.Private)
        val inputBaseIcon: Icon.PackIcon = Icon.PackIcon(
            componentName = inputComponentName,
            packPackageName = ICON_PACK_PACKAGE_NAME,
            resId = PACK_ICON_RES_ID
        )
        val inputState: ProfileSourceState = ProfileSourceState(profiles = listOf(inputProfile))
        val repository: IIconRepositoryImpl = createRepository(state = inputState)
        database.getPackIconDao().insert(inputBaseIcon)

        val actualIcon: Icon = repository.getIcon(
            packPackageName = ICON_PACK_PACKAGE_NAME,
            componentName = inputComponentName,
            profile = inputProfile
        )

        assertEquals(Icon.ProfileIcon(icon = inputBaseIcon, profile = inputPrivateUser), actualIcon)
    }

    @Test
    fun `refreshes icon packs when an icon pack changes`() = databaseTest {
        val inputState: ProfileSourceState = ProfileSourceState()
        val repository: IIconRepositoryImpl = createRepository(state = inputState)
        finishPendingWork()
        assertFalse(repository.hasIconPack())
        addIconPackActivity()

        inputState.changes.emit(
            ProfileChange.PackageChanged(
                user = inputPersonalUser,
                packageName = ICON_PACK_PACKAGE_NAME
            )
        )
        finishPendingWork()

        assertTrue(repository.hasIconPack())
    }

    @Test
    fun `refreshes icon packs when an icon pack is installed`() = databaseTest {
        val inputState: ProfileSourceState = ProfileSourceState()
        val repository: IIconRepositoryImpl = createRepository(state = inputState)
        finishPendingWork()
        addIconPackActivity()

        inputState.changes.emit(
            ProfileChange.PackageAdded(
                user = inputPersonalUser,
                packageName = ICON_PACK_PACKAGE_NAME
            )
        )
        finishPendingWork()

        assertTrue(repository.hasIconPack())
    }

    @Test
    fun `refreshes icon packs when an icon pack is removed`() = databaseTest {
        addIconPackActivity()
        val inputState: ProfileSourceState = ProfileSourceState()
        val repository: IIconRepositoryImpl = createRepository(state = inputState)
        finishPendingWork()
        assertTrue(repository.hasIconPack())
        removeIconPackActivity()

        inputState.changes.emit(
            ProfileChange.PackageRemoved(
                user = inputPersonalUser,
                packageName = ICON_PACK_PACKAGE_NAME
            )
        )
        finishPendingWork()

        assertFalse(repository.hasIconPack())
    }

    @Test
    fun `does not refresh icon packs when another package changes in any Profile`() = databaseTest {
        val inputState: ProfileSourceState = ProfileSourceState()
        val repository: IIconRepositoryImpl = createRepository(state = inputState)
        finishPendingWork()
        addIconPackActivity()
        val inputChanges: List<ProfileChange> = listOf(
            ProfileChange.PackageAdded(user = inputPersonalUser, packageName = APP_PACKAGE_NAME),
            ProfileChange.PackageChanged(user = inputWorkUser, packageName = APP_PACKAGE_NAME),
            ProfileChange.PackageRemoved(user = inputPrivateUser, packageName = APP_PACKAGE_NAME)
        )

        inputChanges.forEach { change -> inputState.changes.emit(change) }
        finishPendingWork()

        assertFalse(repository.hasIconPack())
    }

    private fun TestScope.createRepository(state: ProfileSourceState): IIconRepositoryImpl = IIconRepositoryImpl(
        context = RuntimeEnvironment.getApplication(),
        iconPackDao = database.getCachedIconPackDao(),
        iconDao = database.getPackIconDao(),
        profileSource = createProfileSourceMock(state = state),
        scope = backgroundScope,
        dispatcher = databaseDispatcher
    )

    private fun IIconRepositoryImpl.hasIconPack(): Boolean = iconPacks.value.any { pack ->
        pack is IconPack.CustomIconPack && pack.packageName == ICON_PACK_PACKAGE_NAME
    }

    @Suppress("DEPRECATION")
    private fun addIconPackActivity() {
        val inputActivityInfo: ActivityInfo = ActivityInfo().apply {
            packageName = ICON_PACK_PACKAGE_NAME
            name = "$ICON_PACK_PACKAGE_NAME.IconPack"
            nonLocalizedLabel = ICON_PACK_LABEL
        }
        val inputResolveInfo: ResolveInfo = ResolveInfo().apply {
            activityInfo = inputActivityInfo
        }
        shadowOf(RuntimeEnvironment.getApplication().packageManager).addResolveInfoForIntent(
            Intent("com.novalauncher.THEME"),
            inputResolveInfo
        )
    }

    @Suppress("DEPRECATION")
    private fun removeIconPackActivity() {
        shadowOf(RuntimeEnvironment.getApplication().packageManager).removeResolveInfosForIntent(
            Intent("com.novalauncher.THEME"),
            ICON_PACK_PACKAGE_NAME
        )
    }
}
