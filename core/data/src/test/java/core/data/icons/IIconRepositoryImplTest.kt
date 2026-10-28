package core.data.icons

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import core.data.icons.model.IconPack
import core.data.profiles.ProfileChange
import core.data.testing.InMemoryDatabaseTest
import core.data.testing.PERSONAL_USER_ID
import core.data.testing.ProfileSourceState
import core.data.testing.createProfileSourceMock
import core.data.testing.createUserHandle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

private const val ICON_PACK_PACKAGE_NAME: String = "com.example.iconpack"
private const val ICON_PACK_LABEL: String = "Example icon pack"

@OptIn(ExperimentalCoroutinesApi::class)
class IIconRepositoryImplTest : InMemoryDatabaseTest() {
    @Suppress("DEPRECATION")
    @Test
    fun `refreshes icon packs when a package changes`() = databaseTest {
        val inputState: ProfileSourceState = ProfileSourceState()
        val repository: IIconRepositoryImpl = createRepository(state = inputState)
        finishPendingWork()
        assertTrue(repository.iconPacks.value.none { pack -> pack is IconPack.CustomIconPack })
        addIconPackActivity()

        inputState.changes.emit(
            ProfileChange.PackageChanged(
                user = createUserHandle(userId = PERSONAL_USER_ID),
                packageName = ICON_PACK_PACKAGE_NAME
            )
        )
        finishPendingWork()

        assertTrue(
            repository.iconPacks.value.any { pack ->
                pack is IconPack.CustomIconPack && pack.packageName == ICON_PACK_PACKAGE_NAME
            }
        )
    }

    private fun TestScope.createRepository(state: ProfileSourceState): IIconRepositoryImpl = IIconRepositoryImpl(
        context = RuntimeEnvironment.getApplication(),
        iconPackDao = database.getCachedIconPackDao(),
        iconDao = database.getPackIconDao(),
        profileSource = createProfileSourceMock(state = state),
        scope = backgroundScope,
        dispatcher = databaseDispatcher
    )

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
}
