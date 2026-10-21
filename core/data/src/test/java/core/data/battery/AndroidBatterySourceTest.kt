package core.data.battery

import android.content.Intent
import android.os.BatteryManager
import core.data.testing.TEST_SDK
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [TEST_SDK])
class AndroidBatterySourceTest {
    @Test
    fun `converts level and scale into a percentage`() {
        val status: BatteryStatus? = intent(level = 42, scale = 50).toBatteryStatus()

        assertEquals(BatteryStatus(percent = 84, isCharging = false), status)
    }

    @Test
    fun `treats charging and full batteries as charging`() {
        assertTrue(intent(status = BatteryManager.BATTERY_STATUS_CHARGING).toBatteryStatus()?.isCharging == true)
        assertTrue(intent(status = BatteryManager.BATTERY_STATUS_FULL).toBatteryStatus()?.isCharging == true)
    }

    @Test
    fun `treats a plugged battery as charging`() {
        val status: BatteryStatus? = intent(plugged = BatteryManager.BATTERY_PLUGGED_USB).toBatteryStatus()

        assertFalse(status?.isCharging == false)
        assertTrue(status?.isCharging == true)
    }

    @Test
    fun `reports an unknown status when the scale is invalid`() {
        assertNull(intent(level = 42, scale = 0).toBatteryStatus())
    }

    @Test
    fun `reports an unknown status when the level is missing`() {
        assertNull(intent(level = -1, scale = 100).toBatteryStatus())
    }

    private fun intent(
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
