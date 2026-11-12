package feature.launcher

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import core.data.apps.model.App
import core.data.icons.model.Icon
import core.data.profiles.IProfileSource
import core.data.testing.TEST_SDK
import core.data.testing.createApp
import core.data.testing.createProfileSourceMock
import core.data.widgets.WidgetAvailability
import core.data.widgets.WidgetProvider
import core.ui.R as CoreUiR
import core.ui.composables.RowTestContent
import core.ui.composables.SHEET_TITLE_ICON_TEST_TAG
import core.ui.composables.assertSheetTitleMatchesFirstRow
import core.ui.popups.IPopupEditor
import core.ui.popups.IPopupFolderActions
import core.ui.widgets.AppWidgetModel
import io.mockk.mockk
import io.mockk.verify
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val APP_NAME: String = "Maps"
private const val WIDGET_LABEL: String = "Weather"
private const val WIDGET_PROVIDER_PACKAGE: String = "com.example.weather"
private const val WIDGET_PROVIDER_CLASS: String = "com.example.weather.WeatherWidget"
private const val WIDGET_ID: Long = 1L
private const val APP_WIDGET_ID: Int = 2

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [TEST_SDK])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PopupMenuSheetTest {
    @get:Rule
    val composeRule: ComposeContentTestRule = createComposeRule()

    private val mockCard: PopupCardViewModel = mockk(relaxed = true)
    private val mockEditor: IPopupEditor = mockk(relaxed = true)
    private val mockFolderActions: IPopupFolderActions = mockk(relaxed = true)
    private val mockProfileSource: IProfileSource = createProfileSourceMock()
    private val inputApp: App = createApp(name = APP_NAME)
    private val inputLaunchableMenuState: PopupMenuState.Open = PopupMenuState.Open(
        target = inputApp,
        items = listOf(PopupMenuItem.EditPopup, PopupMenuItem.AddWidget, PopupMenuItem.AppInfo)
    )
    private val inputWidgetMenuState: PopupMenuState.Open = PopupMenuState.Open(
        target = inputApp,
        items = listOf(
            PopupMenuItem.AddToStack,
            PopupMenuItem.ResizeWidget,
            PopupMenuItem.ReconfigureWidget,
            PopupMenuItem.RemoveWidget,
            PopupMenuItem.AppInfo
        ),
        widgetPage = PopupWidgetPage(
            widgetId = WIDGET_ID,
            appWidgetId = APP_WIDGET_ID,
            provider = WidgetProvider(packageName = WIDGET_PROVIDER_PACKAGE, className = WIDGET_PROVIDER_CLASS),
            profileSerial = 0L,
            model = AppWidgetModel(
                id = WIDGET_ID,
                appWidgetId = APP_WIDGET_ID,
                provider = WIDGET_PROVIDER_PACKAGE,
                label = WIDGET_LABEL,
                icon = Icon.RailIcon(resId = 0),
                minHeightDp = 0,
                isWorkProfile = false,
                availability = WidgetAvailability.Available
            ),
            isReconfigurable = true
        )
    )

    @Test
    fun `paints the Launchable menu rows with the sheet container colour`() {
        assertRowsPaintSheetColor(
            state = inputLaunchableMenuState,
            labels = listOf(CoreUiR.string.edit_popup, CoreUiR.string.add_widget, CoreUiR.string.app_info)
        )
    }

    @Test
    fun `paints the widget page menu rows with the sheet container colour`() {
        assertRowsPaintSheetColor(
            state = inputWidgetMenuState,
            labels = listOf(
                CoreUiR.string.add_next_to,
                CoreUiR.string.resize_widget,
                CoreUiR.string.reconfigure_widget,
                CoreUiR.string.remove_widget,
                CoreUiR.string.app_info
            )
        )
    }

    @Test
    fun `lines the Launchable title up with the row content`() {
        setMenuContent(state = inputLaunchableMenuState)

        composeRule.assertSheetTitleMatchesFirstRow(
            titleContent = composeRule.onNodeWithTag(SHEET_TITLE_ICON_TEST_TAG, useUnmergedTree = true)
        )
    }

    @Test
    fun `lines the widget page title up with the row content`() {
        setMenuContent(state = inputWidgetMenuState)

        composeRule.assertSheetTitleMatchesFirstRow(
            titleContent = composeRule.onNodeWithTag(SHEET_TITLE_ICON_TEST_TAG, useUnmergedTree = true)
        )
    }

    @Test
    fun `opens the Pop-up editor from the Edit Pop-up row`() {
        setMenuContent(state = inputLaunchableMenuState)

        composeRule.onNodeWithText(getString(CoreUiR.string.edit_popup)).performClick()

        verify(exactly = 1) { mockEditor.startEdit(inputApp) }
    }

    private fun assertRowsPaintSheetColor(state: PopupMenuState.Open, labels: List<Int>) {
        composeRule.assertSheetRowsPaintSheetColor(content = { MenuContent(state) }, labels = labels)
    }

    private fun setMenuContent(state: PopupMenuState.Open) {
        composeRule.setContent {
            RowTestContent {
                MenuContent(state)
            }
        }
    }

    @Composable
    private fun MenuContent(state: PopupMenuState.Open) {
        PopupMenuContent(
            state = state,
            profileSource = mockProfileSource,
            card = mockCard,
            editor = mockEditor,
            folderActions = mockFolderActions
        )
    }
}
