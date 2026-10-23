package core.data.battery

import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.Looper
import app.cash.turbine.test
import core.data.testing.TEST_SDK
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [TEST_SDK])
class IBatterySourceImplTest {
    @Test
    fun `converts level and scale into a percentage`() {
        val actualStatus: BatteryStatus? = createBatteryIntent(level = 42, scale = 50).toBatteryStatus()

        assertEquals(BatteryStatus(percent = 84, isCharging = false), actualStatus)
    }

    @Test
    fun `treats charging and full batteries as charging`() {
        assertTrue(createBatteryIntent(status = BatteryManager.BATTERY_STATUS_CHARGING).toBatteryStatus()?.isCharging == true)
        assertTrue(createBatteryIntent(status = BatteryManager.BATTERY_STATUS_FULL).toBatteryStatus()?.isCharging == true)
    }

    @Test
    fun `treats a plugged battery as charging`() {
        val actualStatus: BatteryStatus? = createBatteryIntent(plugged = BatteryManager.BATTERY_PLUGGED_USB).toBatteryStatus()

        assertFalse(actualStatus?.isCharging == false)
        assertTrue(actualStatus?.isCharging == true)
    }

    @Test
    fun `reports an unknown status when the scale is invalid`() {
        assertNull(createBatteryIntent(level = 42, scale = 0).toBatteryStatus())
    }

    @Test
    fun `reports an unknown status when the level is missing`() {
        assertNull(createBatteryIntent(level = -1, scale = 100).toBatteryStatus())
    }

    @Test
    fun `publishes the battery status broadcast`() = runTest {
        val inputContext: Context = RuntimeEnvironment.getApplication()
        val source: IBatterySourceImpl = IBatterySourceImpl(context = inputContext, scope = backgroundScope)
        val inputBatteryIntent: Intent = createBatteryIntent(level = 42, scale = 50)

        source.status.test {
            runCurrent()
            inputContext.sendBroadcast(inputBatteryIntent)
            shadowOf(Looper.getMainLooper()).idle()

            assertEquals(BatteryStatus(percent = 84, isCharging = false), awaitItem())
        }
    }

    private fun createBatteryIntent(
        level: Int = 50,
        scale: Int = 100,
        status: Int = BatteryManager.BATTERY_STATUS_DISCHARGING,
        plugged: Int = 0
    ): Intent = Intent(Intent.ACTION_BATTERY_CHANGED)
        .putExtra(BatteryManager.EXTRA_LEVEL, level)
        .putExtra(BatteryManager.EXTRA_SCALE, scale)
        .putExtra(BatteryManager.EXTRA_STATUS, status)
        .putExtra(BatteryManager.EXTRA_PLUGGED, plugged)
}
