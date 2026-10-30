package feature.launcher.widgets

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.UserHandle
import core.data.profiles.IProfileSource
import core.data.profiles.ProfileInfo
import core.data.profiles.ProfileKind
import core.data.testing.PERSONAL_USER_ID
import core.data.testing.ProfileSourceState
import core.data.testing.TEST_SDK
import core.data.testing.WORK_USER_ID
import core.data.testing.createProfileInfo
import core.data.testing.createProfileSourceMock
import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlin.math.roundToInt
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

private const val BADGED_CANVAS_SIZE: Int = 64
private const val BADGE_START: Int = 40
private const val BADGE_SIZE: Int = 8

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [TEST_SDK])
class AppWidgetViewsTest {
    private val application: Application = RuntimeEnvironment.getApplication()
    private val personalProfile: ProfileInfo = createProfileInfo(userId = PERSONAL_USER_ID)
    private val workProfile: ProfileInfo = createProfileInfo(userId = WORK_USER_ID, kind = ProfileKind.Work)

    @Test
    fun `loads a work Profile's system badge`() = runTest {
        val inputState: ProfileSourceState = ProfileSourceState(profiles = listOf(workProfile))
        val mockProfileSource: IProfileSource = createProfileSourceMock(state = inputState)
        val inputBadged: BitmapDrawable = badgedDrawable()
        val baseSlot: CapturingSlot<Drawable> = slot()
        val userSlot: CapturingSlot<UserHandle> = slot()
        val mockPackageManager: PackageManager = mockk()
        every { mockPackageManager.getUserBadgedIcon(capture(baseSlot), capture(userSlot)) } returns inputBadged

        val actualBadge: Drawable? = AppWidgetViews.loadProfileBadge(
            context = contextWith(packageManager = mockPackageManager),
            profileSerial = workProfile.serial,
            profileSource = mockProfileSource
        )

        val actualBitmapBadge: BitmapDrawable = actualBadge as BitmapDrawable
        assertEquals(BADGE_SIZE, actualBitmapBadge.intrinsicWidth)
        assertEquals(BADGE_SIZE, actualBitmapBadge.intrinsicHeight)
        val expectedCanvasSize: Int = (PROFILE_BADGE_CANVAS_DP * application.resources.displayMetrics.density).roundToInt()
        assertEquals(workProfile.user, userSlot.captured)
        assertEquals(expectedCanvasSize, baseSlot.captured.intrinsicWidth)
        assertEquals(expectedCanvasSize, baseSlot.captured.intrinsicHeight)
        verify(exactly = 1) { mockPackageManager.getUserBadgedIcon(any(), workProfile.user) }
    }

    @Test
    fun `takes no badge for the personal Profile`() = runTest {
        val inputState: ProfileSourceState = ProfileSourceState(profiles = listOf(personalProfile))
        val mockProfileSource: IProfileSource = createProfileSourceMock(state = inputState)
        val mockPackageManager: PackageManager = mockk(relaxed = true)

        val actualBadge: Drawable? = AppWidgetViews.loadProfileBadge(
            context = contextWith(packageManager = mockPackageManager),
            profileSerial = personalProfile.serial,
            profileSource = mockProfileSource
        )

        assertNull(actualBadge)
        verify(exactly = 0) { mockPackageManager.getUserBadgedIcon(any(), any()) }
    }

    private fun badgedDrawable(): BitmapDrawable {
        val bitmap: Bitmap = Bitmap.createBitmap(BADGED_CANVAS_SIZE, BADGED_CANVAS_SIZE, Bitmap.Config.ARGB_8888)
        for (x in BADGE_START until BADGE_START + BADGE_SIZE) {
            for (y in BADGE_START until BADGE_START + BADGE_SIZE) {
                bitmap.setPixel(x, y, Color.RED)
            }
        }
        return BitmapDrawable(application.resources, bitmap)
    }

    private fun contextWith(packageManager: PackageManager): Context = object : ContextWrapper(application) {
        override fun getPackageManager(): PackageManager = packageManager
    }
}
