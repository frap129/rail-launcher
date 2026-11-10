package feature.launcher

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import core.data.apps.model.App
import core.data.folders.Folder
import core.data.launchables.model.Launchable
import core.data.testing.TEST_SDK
import core.data.testing.createApp
import core.data.testing.createShortcut
import core.ui.R as CoreUiR
import core.ui.popups.IIconPicker
import core.ui.popups.IPopupCollaborators
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

private const val APP_NAME: String = "Maps"
private const val FOLDER_NAME: String = "Music"
private const val SHORTCUT_NAME: String = "Website"
private const val FOLDER_ID: Long = 7L
private const val SHORTCUT_PACKAGE_NAME: String = "com.example.site"

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [TEST_SDK])
class LauncherMenuTest {
    @get:Rule
    val composeRule: ComposeContentTestRule = createComposeRule()

    private val mockViewModel: LauncherViewModel = mockk(relaxed = true)
    private val mockIconPicker: IIconPicker = mockk(relaxed = true)
    private val mockCollaborators: IPopupCollaborators = mockk(relaxed = true)
    private val changeIconDescription: String
        get() = RuntimeEnvironment.getApplication().getString(CoreUiR.string.change_icon)
    private val removeSuggestionLabel: String
        get() = RuntimeEnvironment.getApplication().getString(R.string.remove_from_suggestions)

    @Test
    fun `opens the Icon picker when an App's header icon is tapped`() {
        assertHeaderIconStartsPicking(item = createApp(name = APP_NAME))
    }

    @Test
    fun `opens the Icon picker when a Shortcut's header icon is tapped`() {
        assertHeaderIconStartsPicking(
            item = createShortcut(packageName = SHORTCUT_PACKAGE_NAME, shortLabel = SHORTCUT_NAME)
        )
    }

    @Test
    fun `opens the Icon picker when a Folder's header icon is tapped`() {
        assertHeaderIconStartsPicking(item = Folder(folderId = FOLDER_ID, name = FOLDER_NAME))
    }

    @Test
    fun `renames the Launchable when the header title is tapped`() {
        val item: App = createApp(name = APP_NAME)
        setMenuContent(item = item)

        composeRule.onNodeWithText(APP_NAME).performClick()

        verify(exactly = 1) { mockViewModel.openItemRename(item) }
        verify(exactly = 0) { mockIconPicker.startPicking(any()) }
    }

    @Test
    fun `removes a Search suggestion from its item menu`() {
        val item: App = createApp(name = APP_NAME)
        setMenuContent(item = item, isSearchSuggestion = true)

        composeRule.onNodeWithText(removeSuggestionLabel).performClick()

        verify(exactly = 1) { mockViewModel.removeSearchSuggestion(item) }
    }

    @Test
    fun `hides the remove suggestion action for a normal row`() {
        setMenuContent(item = createApp(name = APP_NAME), isSearchSuggestion = false)

        composeRule.onNodeWithText(removeSuggestionLabel).assertDoesNotExist()
    }

    private fun assertHeaderIconStartsPicking(item: Launchable) {
        setMenuContent(item = item)

        composeRule.onNodeWithContentDescription(changeIconDescription)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .performClick()

        verify(exactly = 1) { mockViewModel.closeItemMenu() }
        verify(exactly = 1) { mockIconPicker.startPicking(item) }
    }

    private fun setMenuContent(item: Launchable, isSearchSuggestion: Boolean = false) {
        every { mockViewModel.menuState } returns MutableStateFlow(
            LauncherMenuState.Menu(item = item, isSearchSuggestion = isSearchSuggestion)
        )
        every { mockCollaborators.iconPicker } returns mockIconPicker
        composeRule.setContent {
            LauncherMenu(viewModel = mockViewModel, collaborators = mockCollaborators)
        }
    }
}
