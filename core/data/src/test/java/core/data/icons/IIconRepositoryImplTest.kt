package core.data.icons

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import android.os.UserHandle
import app.cash.turbine.test
import core.data.apps.model.App
import core.data.folders.Folder
import core.data.icons.model.CustomIcon
import core.data.icons.model.Icon
import core.data.icons.model.IconPack
import core.data.icons.model.IconPackCategory
import core.data.profiles.ProfileChange
import core.data.profiles.ProfileInfo
import core.data.profiles.ProfileKind
import core.data.shortcuts.ShortcutLaunchable
import core.data.testing.InMemoryDatabaseTest
import core.data.testing.PERSONAL_USER_ID
import core.data.testing.PRIVATE_USER_ID
import core.data.testing.ProfileSourceState
import core.data.testing.WORK_USER_ID
import core.data.testing.createApp
import core.data.testing.createProfileInfo
import core.data.testing.createProfileSourceMock
import core.data.testing.createUserHandle
import core.util.DrawableCategory
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

private const val ICON_PACK_PACKAGE_NAME: String = "com.example.iconpack"
private const val ICON_PACK_LABEL: String = "Example icon pack"
private const val OTHER_ICON_PACK_PACKAGE_NAME: String = "com.example.othericonpack"
private const val OTHER_ICON_PACK_LABEL: String = "Second icon pack"
private const val UNMAPPED_ICON_PACK_PACKAGE_NAME: String = "com.example.unmappediconpack"
private const val UNMAPPED_ICON_PACK_LABEL: String = "Third icon pack"
private const val APP_PACKAGE_NAME: String = "com.example.app"
private const val APP_ACTIVITY_NAME: String = "com.example.app.MainActivity"
private const val PACK_ICON_RES_ID: Int = 42
private const val OTHER_PACK_ICON_RES_ID: Int = 43

@OptIn(ExperimentalCoroutinesApi::class)
class IIconRepositoryImplTest : InMemoryDatabaseTest() {
    private val inputPersonalUser: UserHandle = createUserHandle(userId = PERSONAL_USER_ID)
    private val inputWorkUser: UserHandle = createUserHandle(userId = WORK_USER_ID)
    private val inputPrivateUser: UserHandle = createUserHandle(userId = PRIVATE_USER_ID)
    private val inputComponentName: ComponentName = ComponentName(APP_PACKAGE_NAME, APP_ACTIVITY_NAME)
    private val mockIconPackSource: IIconPackSource = mockk()

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
    fun `maps a pack's drawable categories to icons in the order its source gives them`() = databaseTest {
        val inputState: ProfileSourceState = ProfileSourceState()
        every { mockIconPackSource.drawableCategories(ICON_PACK_PACKAGE_NAME) } returns listOf(
            DrawableCategory(name = "Calendar", drawableNames = listOf("calendar", "mail")),
            DrawableCategory(name = null, drawableNames = listOf("phone"))
        )
        val repository: IIconRepositoryImpl = createRepository(state = inputState)

        val actualCategories: List<IconPackCategory> = repository.getPackCategories(packPackageName = ICON_PACK_PACKAGE_NAME)

        assertEquals(
            listOf(
                IconPackCategory(
                    name = "Calendar",
                    icons = listOf(
                        Icon.PackDrawableIcon(packPackageName = ICON_PACK_PACKAGE_NAME, drawableName = "calendar"),
                        Icon.PackDrawableIcon(packPackageName = ICON_PACK_PACKAGE_NAME, drawableName = "mail")
                    )
                ),
                IconPackCategory(
                    name = null,
                    icons = listOf(Icon.PackDrawableIcon(packPackageName = ICON_PACK_PACKAGE_NAME, drawableName = "phone"))
                )
            ),
            actualCategories
        )
    }

    @Test
    fun `suggests an App's original icon then every installed pack that maps it`() = databaseTest {
        addIconPackActivity(packageName = UNMAPPED_ICON_PACK_PACKAGE_NAME, label = UNMAPPED_ICON_PACK_LABEL)
        addIconPackActivity(packageName = OTHER_ICON_PACK_PACKAGE_NAME, label = OTHER_ICON_PACK_LABEL)
        addIconPackActivity(packageName = ICON_PACK_PACKAGE_NAME, label = ICON_PACK_LABEL)
        every { mockIconPackSource.resolveDrawableName(ICON_PACK_PACKAGE_NAME, PACK_ICON_RES_ID) } returns "calendar"
        every { mockIconPackSource.resolveDrawableName(OTHER_ICON_PACK_PACKAGE_NAME, OTHER_PACK_ICON_RES_ID) } returns "mail"
        val inputApp: App =
            createApp(name = "Example", packageName = APP_PACKAGE_NAME, profileInfo = createProfileInfo(userId = PERSONAL_USER_ID))
        val repository: IIconRepositoryImpl = createRepository(state = ProfileSourceState())
        finishPendingWork()
        database.getPackIconDao().insert(
            Icon.PackIcon(componentName = inputComponentName, packPackageName = ICON_PACK_PACKAGE_NAME, resId = PACK_ICON_RES_ID)
        )
        database.getPackIconDao().insert(
            Icon.PackIcon(
                componentName = inputComponentName,
                packPackageName = OTHER_ICON_PACK_PACKAGE_NAME,
                resId = OTHER_PACK_ICON_RES_ID
            )
        )

        repository.getSuggestedIcons(launchable = inputApp).test {
            runCurrent()

            assertEquals(listOf(inputApp.defaultIcon), awaitItem())
            assertEquals(
                listOf(
                    inputApp.defaultIcon,
                    Icon.PackDrawableIcon(packPackageName = ICON_PACK_PACKAGE_NAME, drawableName = "calendar")
                ),
                awaitItem()
            )
            assertEquals(
                listOf(
                    inputApp.defaultIcon,
                    Icon.PackDrawableIcon(packPackageName = ICON_PACK_PACKAGE_NAME, drawableName = "calendar"),
                    Icon.PackDrawableIcon(packPackageName = OTHER_ICON_PACK_PACKAGE_NAME, drawableName = "mail")
                ),
                awaitItem()
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `suggests pack icons whose names contain a Folder label word`() = databaseTest {
        addIconPackActivity()
        every { mockIconPackSource.drawableCategories(ICON_PACK_PACKAGE_NAME) } returns listOf(
            DrawableCategory(name = null, drawableNames = listOf("music_note", "video_player", "calculator"))
        )
        val inputFolder: Folder = Folder(folderId = 1L, name = "Music Player")
        val repository: IIconRepositoryImpl = createRepository(state = ProfileSourceState())
        finishPendingWork()

        repository.getSuggestedIcons(launchable = inputFolder).test {
            runCurrent()

            assertEquals(
                listOf(
                    Icon.PackDrawableIcon(packPackageName = ICON_PACK_PACKAGE_NAME, drawableName = "music_note"),
                    Icon.PackDrawableIcon(packPackageName = ICON_PACK_PACKAGE_NAME, drawableName = "video_player")
                ),
                expectMostRecentItem()
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `suggests pack icons whose names contain a Shortcut label word`() = databaseTest {
        addIconPackActivity()
        every { mockIconPackSource.drawableCategories(ICON_PACK_PACKAGE_NAME) } returns listOf(
            DrawableCategory(name = null, drawableNames = listOf("music_note", "calculator"))
        )
        val inputShortcut: ShortcutLaunchable = ShortcutLaunchable(
            packageName = APP_PACKAGE_NAME,
            shortcutId = "shortcut-id",
            profile = inputPersonalUser,
            shortLabel = "Music",
            longLabel = "Music"
        )
        val repository: IIconRepositoryImpl = createRepository(state = ProfileSourceState())
        finishPendingWork()

        repository.getSuggestedIcons(launchable = inputShortcut).test {
            runCurrent()

            assertEquals(
                listOf(Icon.PackDrawableIcon(packPackageName = ICON_PACK_PACKAGE_NAME, drawableName = "music_note")),
                expectMostRecentItem()
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `ignores label words shorter than three characters`() = databaseTest {
        addIconPackActivity()
        every { mockIconPackSource.drawableCategories(ICON_PACK_PACKAGE_NAME) } returns listOf(
            DrawableCategory(name = null, drawableNames = listOf("tv_channel"))
        )
        val inputFolder: Folder = Folder(folderId = 1L, name = "TV")
        val repository: IIconRepositoryImpl = createRepository(state = ProfileSourceState())
        finishPendingWork()

        repository.getSuggestedIcons(launchable = inputFolder).test {
            runCurrent()

            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `caps label suggestions at 12`() = databaseTest {
        addIconPackActivity()
        val inputDrawableNames: List<String> = (1..13).map { index -> "music_$index" }
        every { mockIconPackSource.drawableCategories(ICON_PACK_PACKAGE_NAME) } returns listOf(
            DrawableCategory(name = null, drawableNames = inputDrawableNames)
        )
        val inputFolder: Folder = Folder(folderId = 1L, name = "Music")
        val repository: IIconRepositoryImpl = createRepository(state = ProfileSourceState())
        finishPendingWork()

        repository.getSuggestedIcons(launchable = inputFolder).test {
            runCurrent()

            val actualSuggestions: List<Icon> = expectMostRecentItem()
            assertEquals(12, actualSuggestions.size)
            assertEquals(
                inputDrawableNames.take(12).map { drawableName ->
                    Icon.PackDrawableIcon(packPackageName = ICON_PACK_PACKAGE_NAME, drawableName = drawableName)
                },
                actualSuggestions
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `resolves a pack drawable Custom icon and badges it for every non-personal Profile`() = databaseTest {
        val inputCustomIcon: CustomIcon = CustomIcon.PackDrawable(
            packPackageName = ICON_PACK_PACKAGE_NAME,
            drawableName = "calendar"
        )
        every { mockIconPackSource.hasDrawable(ICON_PACK_PACKAGE_NAME, "calendar") } returns true
        val inputProfiles: List<ProfileInfo> = listOf(
            createProfileInfo(userId = WORK_USER_ID, kind = ProfileKind.Work),
            createProfileInfo(userId = PRIVATE_USER_ID, kind = ProfileKind.Private)
        )
        val repository: IIconRepositoryImpl = createRepository(state = ProfileSourceState(profiles = inputProfiles))

        inputProfiles.forEach { inputProfile ->
            val actualIcon: Icon? = repository.getCustomIcon(customIcon = inputCustomIcon, profile = inputProfile)

            assertEquals(
                Icon.ProfileIcon(
                    icon = Icon.PackDrawableIcon(packPackageName = ICON_PACK_PACKAGE_NAME, drawableName = "calendar"),
                    profile = inputProfile.user
                ),
                actualIcon
            )
        }
    }

    @Test
    fun `returns no Custom icon when its pack dropped the drawable`() = databaseTest {
        val inputCustomIcon: CustomIcon = CustomIcon.PackDrawable(
            packPackageName = ICON_PACK_PACKAGE_NAME,
            drawableName = "calendar"
        )
        every { mockIconPackSource.hasDrawable(ICON_PACK_PACKAGE_NAME, "calendar") } returns false
        val repository: IIconRepositoryImpl = createRepository(state = ProfileSourceState())

        val actualIcon: Icon? = repository.getCustomIcon(
            customIcon = inputCustomIcon,
            profile = createProfileInfo(userId = PERSONAL_USER_ID)
        )

        assertNull(actualIcon)
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
        iconPackSource = mockIconPackSource,
        profileSource = createProfileSourceMock(state = state),
        scope = backgroundScope,
        dispatcher = databaseDispatcher
    )

    private fun IIconRepositoryImpl.hasIconPack(): Boolean = iconPacks.value.any { pack ->
        pack is IconPack.CustomIconPack && pack.packageName == ICON_PACK_PACKAGE_NAME
    }

    @Suppress("DEPRECATION")
    private fun addIconPackActivity(packageName: String = ICON_PACK_PACKAGE_NAME, label: String = ICON_PACK_LABEL) {
        val inputActivityInfo: ActivityInfo = ActivityInfo().apply {
            this.packageName = packageName
            name = "$packageName.IconPack"
            nonLocalizedLabel = label
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
