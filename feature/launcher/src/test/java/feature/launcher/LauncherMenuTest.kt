package feature.launcher

import android.content.Context
import android.content.pm.LauncherApps
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import core.data.apps.model.App
import core.data.folders.Folder
import core.data.launchables.model.Launchable
import core.data.profiles.ProfileInfo
import core.data.profiles.ProfileKind
import core.data.testing.PRIVATE_USER_ID
import core.data.testing.TEST_SDK
import core.data.testing.createApp
import core.data.testing.createProfileInfo
import core.data.testing.createShortcut
import core.ui.R as CoreUiR
import core.ui.composables.RowTestContent
import core.ui.composables.SHEET_TITLE_ICON_TEST_TAG
import core.ui.composables.assertSheetRowsPaintSheetColor
import core.ui.composables.assertSheetTitleMatchesFirstRow
import core.ui.composables.getString
import core.ui.popups.IIconPicker
import core.ui.popups.IPopupCollaborators
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val APP_NAME: String = "Maps"
private const val FOLDER_NAME: String = "Music"
private const val SHORTCUT_NAME: String = "Website"
private const val FOLDER_ID: Long = 7L
private const val SHORTCUT_PACKAGE_NAME: String = "com.example.site"

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [TEST_SDK])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LauncherMenuTest {
    @get:Rule
    val composeRule: ComposeContentTestRule = createComposeRule()

    private val mockViewModel: LauncherViewModel = mockk(relaxed = true)
    private val mockIconPicker: IIconPicker = mockk(relaxed = true)
    private val mockCollaborators: IPopupCollaborators = mockk(relaxed = true)
    private val inputPrivateProfile: ProfileInfo = createProfileInfo(userId = PRIVATE_USER_ID, kind = ProfileKind.Private)
    private val changeIconDescription: String
        get() = getString(CoreUiR.string.change_icon)
    private val removeSuggestionLabel: String
        get() = getString(R.string.remove_from_suggestions)
    private val removeShortcutLabel: String
        get() = getString(R.string.remove_shortcut)
    private val stopSuggestingLabel: String
        get() = getString(R.string.stop_suggesting)
    private val uninstallLabel: String
        get() = getString(R.string.uninstall)
    private val hideLabel: String
        get() = getString(R.string.hide_from_app_list)
    private val installInPrivateSpaceLabel: String
        get() = getString(R.string.install_in_private_space)
    private var storePageFailureCount: Int = 0
    private val onStorePageFailed: () -> Unit = { storePageFailureCount++ }

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
        setMenuContent(item = item, suggestion = MenuSuggestion.Search)

        composeRule.onNodeWithText(removeSuggestionLabel).performClick()

        verify(exactly = 1) { mockViewModel.removeSearchSuggestion(item) }
    }

    @Test
    fun `stops suggesting a Home suggestion from its item menu`() {
        val item: App = createApp(name = APP_NAME)
        setItemMenuContent(item = item, suggestion = MenuSuggestion.Home)

        composeRule.onNodeWithText(stopSuggestingLabel).performClick()

        verify(exactly = 1) { mockViewModel.stopSuggesting(item) }
    }

    @Test
    fun `hides the Stop suggesting action for a row that is not a Home suggestion`() {
        setItemMenuContent(item = createApp(name = APP_NAME), suggestion = MenuSuggestion.None)

        composeRule.onNodeWithText(stopSuggestingLabel).assertDoesNotExist()
    }

    @Test
    fun `hides the remove suggestion action for a normal row`() {
        setMenuContent(item = createApp(name = APP_NAME), suggestion = MenuSuggestion.None)

        composeRule.onNodeWithText(removeSuggestionLabel).assertDoesNotExist()
    }

    @Test
    fun `hides the remove shortcut action for a Shortcut Search result`() {
        setMenuContent(item = createShortcut(packageName = SHORTCUT_PACKAGE_NAME), isStandaloneShortcut = false)

        composeRule.onNodeWithText(removeShortcutLabel).assertDoesNotExist()
    }

    @Test
    fun `shows the remove shortcut action for a Standalone shortcut`() {
        setMenuContent(item = createShortcut(packageName = SHORTCUT_PACKAGE_NAME), isStandaloneShortcut = true)

        composeRule.onNodeWithText(removeShortcutLabel).assertExists()
    }

    @Test
    fun `shows the Hide from App List action for an App`() {
        setItemMenuContent(item = createApp(name = APP_NAME))

        composeRule.onNodeWithText(hideLabel)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
    }

    @Test
    fun `hides the Hide from App List action for a Folder`() {
        setItemMenuContent(item = Folder(folderId = FOLDER_ID, name = FOLDER_NAME))

        composeRule.onNodeWithText(hideLabel).assertDoesNotExist()
    }

    @Test
    fun `hides the Launchable when the Hide from App List action is tapped`() {
        val item: App = createApp(name = APP_NAME)
        setItemMenuContent(item = item)

        composeRule.onNodeWithText(hideLabel).performClick()

        verify(exactly = 1) { mockViewModel.hideFromAppList(item) }
    }

    @Test
    fun `shows the uninstall action for an App whose Profile allows it`() {
        setItemMenuContent(item = createApp(name = APP_NAME), canUninstall = true)

        composeRule.onNodeWithText(uninstallLabel).assertExists()
    }

    @Test
    fun `closes the sheet when the uninstall action is tapped`() {
        setItemMenuContent(item = createApp(name = APP_NAME), canUninstall = true)

        composeRule.onNodeWithText(uninstallLabel).performClick()

        verify(exactly = 1) { mockViewModel.closeItemMenu() }
    }

    @Test
    fun `hides the uninstall action for an App that cannot be uninstalled`() {
        setItemMenuContent(item = createApp(name = APP_NAME), canUninstall = false)

        composeRule.onNodeWithText(uninstallLabel).assertDoesNotExist()
    }

    @Test
    fun `hides the uninstall action for a Standalone shortcut`() {
        setItemMenuContent(
            item = createShortcut(packageName = SHORTCUT_PACKAGE_NAME, shortLabel = SHORTCUT_NAME),
            isStandaloneShortcut = true,
            canUninstall = true
        )

        composeRule.onNodeWithText(uninstallLabel).assertDoesNotExist()
    }

    @Test
    fun `shows the Install in Private Space action for an App while the Private Space is Unlocked`() {
        setItemMenuContent(item = createApp(name = APP_NAME), installInPrivateSpaceProfile = inputPrivateProfile)

        composeRule.onNodeWithText(installInPrivateSpaceLabel).assertExists()
    }

    @Test
    fun `hides the Install in Private Space action without an Unlocked Private Space`() {
        setItemMenuContent(item = createApp(name = APP_NAME), installInPrivateSpaceProfile = null)

        composeRule.onNodeWithText(installInPrivateSpaceLabel).assertDoesNotExist()
    }

    @Test
    fun `opens the Private Space Store page when the Install in Private Space action is tapped`() {
        val inputApp: App = createApp(name = APP_NAME)

        val mockLauncherApps: LauncherApps = tapInstallInPrivateSpace(item = inputApp)

        verify(exactly = 1) { mockLauncherApps.getAppMarketActivityIntent(inputApp.packageName, inputPrivateProfile.user) }
    }

    @Test
    fun `reports a Store page the system cannot open`() {
        tapInstallInPrivateSpace(item = createApp(name = APP_NAME))

        assertEquals(1, storePageFailureCount)
    }

    @Test
    fun `paints the App menu rows with the sheet container colour`() {
        assertRowsPaintSheetColor(
            item = createApp(name = APP_NAME),
            suggestion = MenuSuggestion.Search,
            canUninstall = true,
            labels = listOf(
                CoreUiR.string.edit_favorites,
                R.string.remove_from_suggestions,
                R.string.hide_from_app_list,
                CoreUiR.string.edit_popup,
                R.string.add_to_folder,
                CoreUiR.string.app_info,
                R.string.uninstall
            )
        )
    }

    @Test
    fun `paints the Folder menu rows with the sheet container colour`() {
        assertRowsPaintSheetColor(
            item = Folder(folderId = FOLDER_ID, name = FOLDER_NAME),
            labels = listOf(CoreUiR.string.edit_favorites, R.string.edit_folder, CoreUiR.string.delete_folder)
        )
    }

    @Test
    fun `paints the Shortcut menu rows with the sheet container colour`() {
        assertRowsPaintSheetColor(
            item = createShortcut(packageName = SHORTCUT_PACKAGE_NAME, shortLabel = SHORTCUT_NAME),
            isStandaloneShortcut = true,
            labels = listOf(
                CoreUiR.string.edit_favorites,
                R.string.hide_from_app_list,
                R.string.add_to_folder,
                CoreUiR.string.app_info,
                R.string.remove_shortcut
            )
        )
    }

    @Test
    fun `lines the sheet title up with the row content`() {
        setItemMenuContent(item = createApp(name = APP_NAME))

        composeRule.assertSheetTitleMatchesFirstRow(
            titleContent = composeRule.onNodeWithTag(SHEET_TITLE_ICON_TEST_TAG, useUnmergedTree = true)
        )
    }

    /** Taps Install in Private Space on [item]'s menu while the system offers no Store page, returning the LauncherApps it asked. */
    private fun tapInstallInPrivateSpace(item: App): LauncherApps {
        val mockLauncherApps: LauncherApps = mockk(relaxed = true)
        every { mockLauncherApps.getAppMarketActivityIntent(any(), any()) } returns null
        shadowOf(RuntimeEnvironment.getApplication()).setSystemService(Context.LAUNCHER_APPS_SERVICE, mockLauncherApps)
        setItemMenuContent(item = item, installInPrivateSpaceProfile = inputPrivateProfile)

        composeRule.onNodeWithText(installInPrivateSpaceLabel).performClick()

        return mockLauncherApps
    }

    private fun assertRowsPaintSheetColor(
        item: Launchable,
        labels: List<Int>,
        suggestion: MenuSuggestion = MenuSuggestion.None,
        isStandaloneShortcut: Boolean = false,
        canUninstall: Boolean = false
    ) {
        val menu: LauncherMenuState.Menu = createItemMenu(
            item = item,
            suggestion = suggestion,
            isStandaloneShortcut = isStandaloneShortcut,
            canUninstall = canUninstall
        )
        composeRule.assertSheetRowsPaintSheetColor(
            content = {
                LauncherItemMenu(
                    menu = menu,
                    viewModel = mockViewModel,
                    collaborators = mockCollaborators,
                    onStorePageFailed = onStorePageFailed
                )
            },
            labels = labels
        )
    }

    private fun assertHeaderIconStartsPicking(item: Launchable) {
        setMenuContent(item = item)

        composeRule.onNodeWithContentDescription(changeIconDescription)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .performClick()

        verify(exactly = 1) { mockViewModel.closeItemMenu() }
        verify(exactly = 1) { mockIconPicker.startPicking(item) }
    }

    private fun setItemMenuContent(
        item: Launchable,
        suggestion: MenuSuggestion = MenuSuggestion.None,
        isStandaloneShortcut: Boolean = false,
        canUninstall: Boolean = false,
        installInPrivateSpaceProfile: ProfileInfo? = null
    ) {
        val menu: LauncherMenuState.Menu = createItemMenu(
            item = item,
            suggestion = suggestion,
            isStandaloneShortcut = isStandaloneShortcut,
            canUninstall = canUninstall,
            installInPrivateSpaceProfile = installInPrivateSpaceProfile
        )
        composeRule.setContent {
            RowTestContent {
                LauncherItemMenu(
                    menu = menu,
                    viewModel = mockViewModel,
                    collaborators = mockCollaborators,
                    onStorePageFailed = onStorePageFailed
                )
            }
        }
    }

    private fun setMenuContent(
        item: Launchable,
        suggestion: MenuSuggestion = MenuSuggestion.None,
        isStandaloneShortcut: Boolean = false,
        canUninstall: Boolean = false
    ) {
        every { mockViewModel.menuState } returns MutableStateFlow(
            createItemMenu(
                item = item,
                suggestion = suggestion,
                isStandaloneShortcut = isStandaloneShortcut,
                canUninstall = canUninstall
            )
        )
        every { mockCollaborators.iconPicker } returns mockIconPicker
        composeRule.setContent {
            LauncherMenu(viewModel = mockViewModel, collaborators = mockCollaborators, onStorePageFailed = onStorePageFailed)
        }
    }
}
