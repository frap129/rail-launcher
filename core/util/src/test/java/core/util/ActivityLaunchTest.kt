package core.util

import android.content.ActivityNotFoundException
import android.content.IntentSender
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityLaunchTest {
    @Test
    fun `reports a completed launch`() {
        assertTrue(attemptLaunch { })
    }

    @Test
    fun `reports a target that cannot open`() {
        assertFalse(attemptLaunch { throw ActivityNotFoundException() })
        assertFalse(attemptLaunch { throw SecurityException() })
        assertFalse(attemptLaunch { throw IllegalStateException() })
        assertFalse(attemptLaunch { throw IntentSender.SendIntentException() })
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rethrows an unexpected failure`() {
        attemptLaunch { throw IllegalArgumentException() }
    }
}
