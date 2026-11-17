package core.lifecycle.startup

import core.data.testing.TEST_SDK
import core.ui.HOME_ICONS_DRAWN_TRACE_SECTION
import core.ui.IHomeIconsDrawn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowTrace

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [TEST_SDK])
class IHomeIconsDrawnImplTest {
    @Before
    fun resetTrace() {
        ShadowTrace.reset()
    }

    @Test
    fun `ends one async trace section once Home icons have drawn`() {
        val homeIconsDrawn: IHomeIconsDrawn = IHomeIconsDrawnImpl()
        homeIconsDrawn.start()
        assertEquals(listOf(HOME_ICONS_SECTION), currentSections())
        homeIconsDrawn.report()
        assertTrue(ShadowTrace.getCurrentAsyncSections().isEmpty())
        assertEquals(listOf(HOME_ICONS_SECTION), ShadowTrace.getPreviousAsyncSections().map(::sectionOf))
    }

    @Test
    fun `reports Home icons drawn once`() {
        val homeIconsDrawn: IHomeIconsDrawn = IHomeIconsDrawnImpl()
        homeIconsDrawn.start()
        homeIconsDrawn.report()
        homeIconsDrawn.report()
        assertEquals(1, ShadowTrace.getPreviousAsyncSections().size)
    }

    private fun currentSections(): List<Pair<String, Int>> = ShadowTrace.getCurrentAsyncSections().map(::sectionOf)

    private fun sectionOf(section: ShadowTrace.AsyncTraceSection): Pair<String, Int> = section.sectionName to section.cookie

    private companion object {
        val HOME_ICONS_SECTION: Pair<String, Int> = HOME_ICONS_DRAWN_TRACE_SECTION to HOME_ICONS_DRAWN_COOKIE
    }
}
