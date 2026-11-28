package core.util

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import core.data.testing.RAIL_PACKAGE_NAME
import core.data.testing.TEST_SDK
import core.data.testing.TimberLogRecorder
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [TEST_SDK])
class ApplicationVersionUtilsTest {
    private val inputFallback: String = "Version unavailable"
    private val mockContext: Context = mockk()
    private val mockPackageManager: PackageManager = mockk()

    @Before
    fun setUp() {
        every { mockContext.packageName } returns RAIL_PACKAGE_NAME
        every { mockContext.packageManager } returns mockPackageManager
    }

    @Test
    fun `reads Rail's package version name`() {
        val inputVersionName: String = "1.2.3"
        every { mockPackageManager.getPackageInfo(RAIL_PACKAGE_NAME, PACKAGE_INFO_FLAGS) } returns PackageInfo().apply {
            versionName = inputVersionName
        }
        val actualVersionName: String = mockContext.readAppVersionName(fallback = inputFallback)
        assertEquals(inputVersionName, actualVersionName)
    }

    @Test
    fun `uses and logs the fallback when Rail's package cannot be read`() {
        every { mockPackageManager.getPackageInfo(RAIL_PACKAGE_NAME, PACKAGE_INFO_FLAGS) } throws
            PackageManager.NameNotFoundException()
        val logRecorder: TimberLogRecorder = TimberLogRecorder()
        logRecorder.record {
            val actualVersionName: String = mockContext.readAppVersionName(fallback = inputFallback)
            assertEquals(inputFallback, actualVersionName)
        }
        logRecorder.assertLogged("Could not read Rail's version name")
    }

    @Test
    fun `uses and logs the fallback when Rail has no version name`() {
        every { mockPackageManager.getPackageInfo(RAIL_PACKAGE_NAME, PACKAGE_INFO_FLAGS) } returns PackageInfo()
        val logRecorder: TimberLogRecorder = TimberLogRecorder()
        logRecorder.record {
            val actualVersionName: String = mockContext.readAppVersionName(fallback = inputFallback)
            assertEquals(inputFallback, actualVersionName)
        }
        logRecorder.assertLogged("Rail's package has no version name")
    }
}
