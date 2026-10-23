package feature.launcher.widgets

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaPlayerEndInsetTest {
    private val expectedMediaPlayerEndInset: Dp = 48.dp

    @Test
    fun `media player end inset stops before the rail touch column`() {
        val actualMediaPlayerEndInset: Dp = mediaPlayerWidthMode.endInset()
        assertEquals(expectedMediaPlayerEndInset, actualMediaPlayerEndInset)
    }
}
