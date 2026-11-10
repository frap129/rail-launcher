package core.lifecycle

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import core.ui.model.data.LauncherSignal
import core.ui.model.data.SEARCH_ENTRY_URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LauncherIntentHandlerTest {
    @Test
    fun `a Search entry on creation emits OpenSearch`() {
        val actualSignals: MutableList<LauncherSignal> = mutableListOf()
        val launcherIntentHandler: LauncherIntentHandler = LauncherIntentHandler(onSignal = actualSignals::add)

        val actualHandled: Boolean = launcherIntentHandler.handle(
            intent = createSearchEntryIntent(),
            wasResumed = false,
            isNewIntent = false
        )

        assertTrue(actualHandled)
        assertEquals(listOf(LauncherSignal.OpenSearch), actualSignals)
    }

    @Test
    fun `a Search entry through a new intent emits OpenSearch`() {
        val actualSignals: MutableList<LauncherSignal> = mutableListOf()
        val launcherIntentHandler: LauncherIntentHandler = LauncherIntentHandler(onSignal = actualSignals::add)

        val actualHandled: Boolean = launcherIntentHandler.handle(
            intent = createSearchEntryIntent(),
            wasResumed = true,
            isNewIntent = true
        )

        assertTrue(actualHandled)
        assertEquals(listOf(LauncherSignal.OpenSearch), actualSignals)
    }

    @Test
    fun `an already handled creation intent emits nothing after recreation`() {
        val inputIntent: Intent = createSearchEntryIntent()
        val firstSignals: MutableList<LauncherSignal> = mutableListOf()
        val firstHandler: LauncherIntentHandler = LauncherIntentHandler(onSignal = firstSignals::add)
        firstHandler.handle(intent = inputIntent, wasResumed = false, isNewIntent = false)
        val savedState: Bundle = Bundle()
        firstHandler.save(outState = savedState)
        val actualSignals: MutableList<LauncherSignal> = mutableListOf()
        val recreatedHandler: LauncherIntentHandler = LauncherIntentHandler(onSignal = actualSignals::add)
        recreatedHandler.restore(savedInstanceState = savedState)

        val actualHandled: Boolean = recreatedHandler.handle(
            intent = inputIntent,
            wasResumed = false,
            isNewIntent = false
        )

        assertFalse(actualHandled)
        assertTrue(actualSignals.isEmpty())
    }

    @Test
    fun `a Search entry on a restored instance is handled when it was not handled before`() {
        val savedState: Bundle = Bundle()
        LauncherIntentHandler(onSignal = {}).save(outState = savedState)
        val actualSignals: MutableList<LauncherSignal> = mutableListOf()
        val launcherIntentHandler: LauncherIntentHandler = LauncherIntentHandler(onSignal = actualSignals::add)
        launcherIntentHandler.restore(savedInstanceState = savedState)

        val actualHandled: Boolean = launcherIntentHandler.handle(
            intent = createSearchEntryIntent(),
            wasResumed = false,
            isNewIntent = false
        )

        assertTrue(actualHandled)
        assertEquals(listOf(LauncherSignal.OpenSearch), actualSignals)
    }

    @Test
    fun `a HOME intent through a new intent emits Home`() {
        val actualSignals: MutableList<LauncherSignal> = mutableListOf()
        val launcherIntentHandler: LauncherIntentHandler = LauncherIntentHandler(onSignal = actualSignals::add)

        val actualHandled: Boolean = launcherIntentHandler.handle(
            intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            wasResumed = true,
            isNewIntent = true
        )

        assertTrue(actualHandled)
        assertEquals(listOf(LauncherSignal.Home(wasResumed = true)), actualSignals)
    }

    @Test
    fun `an unrelated VIEW URI emits nothing`() {
        val actualSignals: MutableList<LauncherSignal> = mutableListOf()
        val launcherIntentHandler: LauncherIntentHandler = LauncherIntentHandler(onSignal = actualSignals::add)

        val actualHandled: Boolean = launcherIntentHandler.handle(
            intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")),
            wasResumed = false,
            isNewIntent = true
        )

        assertFalse(actualHandled)
        assertTrue(actualSignals.isEmpty())
    }

    private fun createSearchEntryIntent(): Intent = Intent(Intent.ACTION_VIEW, Uri.parse(SEARCH_ENTRY_URI))
}
