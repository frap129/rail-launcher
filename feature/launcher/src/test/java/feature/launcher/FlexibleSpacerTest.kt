package feature.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class FlexibleSpacerTest {
    @Test
    fun `fills the space left by the other Home items`() {
        assertEquals(400, flexibleSpacerHeight(viewport = 1000, itemSizes = listOf(200, 400)))
    }

    @Test
    fun `is zero when the content exactly fills Home`() {
        assertEquals(0, flexibleSpacerHeight(viewport = 1000, itemSizes = listOf(400, 600)))
    }

    @Test
    fun `is zero when the content overflows Home`() {
        assertEquals(0, flexibleSpacerHeight(viewport = 1000, itemSizes = listOf(800, 400)))
    }

    @Test
    fun `is the full viewport when Home has no other items`() {
        assertEquals(1000, flexibleSpacerHeight(viewport = 1000, itemSizes = emptyList()))
    }
}
