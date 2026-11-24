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
        val launcherIntentHandler: LauncherIntentHandler = createHandler(actualSignals)
        val actualHandled: Boolean = launcherIntentHandler.handle(
            intent = createSearchEntryIntent(),
            isNewIntent = false
        )
        assertTrue(actualHandled)
        assertEquals(listOf(LauncherSignal.OpenSearch), actualSignals)
    }

    @Test
    fun `a Search entry through a new intent emits OpenSearch`() {
        val actualSignals: MutableList<LauncherSignal> = mutableListOf()
        val launcherIntentHandler: LauncherIntentHandler = createHandler(actualSignals)
        val actualHandled: Boolean = launcherIntentHandler.handle(
            intent = createSearchEntryIntent(),
            isNewIntent = true
        )
        assertTrue(actualHandled)
        assertEquals(listOf(LauncherSignal.OpenSearch), actualSignals)
    }

    @Test
    fun `an already handled creation intent emits nothing after recreation`() {
        val inputIntent: Intent = createSearchEntryIntent()
        val firstSignals: MutableList<LauncherSignal> = mutableListOf()
        val firstHandler: LauncherIntentHandler = createHandler(firstSignals)
        firstHandler.handle(intent = inputIntent, isNewIntent = false)
        val savedState: Bundle = Bundle()
        firstHandler.save(outState = savedState)
        val actualSignals: MutableList<LauncherSignal> = mutableListOf()
        val recreatedHandler: LauncherIntentHandler = createHandler(actualSignals)
        recreatedHandler.restore(savedInstanceState = savedState)
        val actualHandled: Boolean = recreatedHandler.handle(
            intent = inputIntent,
            isNewIntent = false
        )
        assertFalse(actualHandled)
        assertTrue(actualSignals.isEmpty())
    }

    @Test
    fun `a Search entry on a restored instance is handled when it was not handled before`() {
        val savedState: Bundle = Bundle()
        createHandler(mutableListOf()).save(outState = savedState)
        val actualSignals: MutableList<LauncherSignal> = mutableListOf()
        val launcherIntentHandler: LauncherIntentHandler = createHandler(actualSignals)
        launcherIntentHandler.restore(savedInstanceState = savedState)
        val actualHandled: Boolean = launcherIntentHandler.handle(
            intent = createSearchEntryIntent(),
            isNewIntent = false
        )
        assertTrue(actualHandled)
        assertEquals(listOf(LauncherSignal.OpenSearch), actualSignals)
    }

    @Test
    fun `a HOME intent while shown without focus shows Home without Search focus`() {
        val inputHomePressState: HomePressState = HomePressState()
        inputHomePressState.onResume()
        val actualSignals: MutableList<LauncherSignal> = mutableListOf()
        val launcherIntentHandler: LauncherIntentHandler = createHandler(actualSignals, inputHomePressState)
        val actualHandled: Boolean = launcherIntentHandler.handle(
            intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            isNewIntent = true
        )
        assertTrue(actualHandled)
        assertEquals(listOf(LauncherSignal.Home(shouldShowHome = true, wasFocused = false)), actualSignals)
    }

    @Test
    fun `a focused HOME intent while shown keeps Search focus`() {
        val inputHomePressState: HomePressState = HomePressState()
        inputHomePressState.onResume()
        inputHomePressState.onWindowFocusChanged(hasFocus = true)
        inputHomePressState.onPause()
        val actualSignals: MutableList<LauncherSignal> = mutableListOf()
        val launcherIntentHandler: LauncherIntentHandler = createHandler(actualSignals, inputHomePressState)
        val actualHandled: Boolean = launcherIntentHandler.handle(
            intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            isNewIntent = true
        )
        assertTrue(actualHandled)
        assertEquals(listOf(LauncherSignal.Home(shouldShowHome = true, wasFocused = true)), actualSignals)
    }

    @Test
    fun `a HOME intent after another app stopped the launcher preserves the current screen`() {
        val inputHomePressState: HomePressState = HomePressState()
        inputHomePressState.onResume()
        inputHomePressState.onWindowFocusChanged(hasFocus = true)
        inputHomePressState.onPause()
        inputHomePressState.onStop()
        val actualSignals: MutableList<LauncherSignal> = mutableListOf()
        val launcherIntentHandler: LauncherIntentHandler = createHandler(actualSignals, inputHomePressState)
        val actualHandled: Boolean = launcherIntentHandler.handle(
            intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            isNewIntent = true
        )
        assertTrue(actualHandled)
        assertEquals(listOf(LauncherSignal.Home(shouldShowHome = false, wasFocused = false)), actualSignals)
    }

    @Test
    fun `an unrelated VIEW URI emits nothing`() {
        val actualSignals: MutableList<LauncherSignal> = mutableListOf()
        val launcherIntentHandler: LauncherIntentHandler = createHandler(actualSignals)
        val actualHandled: Boolean = launcherIntentHandler.handle(
            intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")),
            isNewIntent = true
        )
        assertFalse(actualHandled)
        assertTrue(actualSignals.isEmpty())
    }

    private fun createHandler(
        actualSignals: MutableList<LauncherSignal>,
        inputHomePressState: HomePressState = HomePressState()
    ): LauncherIntentHandler = LauncherIntentHandler(
        onSignal = actualSignals::add,
        homePressState = inputHomePressState
    )

    private fun createSearchEntryIntent(): Intent = Intent(Intent.ACTION_VIEW, Uri.parse(SEARCH_ENTRY_URI))
}
