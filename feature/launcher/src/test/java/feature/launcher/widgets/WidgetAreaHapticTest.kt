package feature.launcher.widgets

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import core.data.media.MediaCommand
import core.data.prefs.RailSide
import core.data.testing.TEST_SDK
import core.data.testing.WIDGET_LABEL
import core.data.testing.createMediaSessionSnapshot
import core.data.widgets.WidgetAvailability
import core.ui.widgets.AppWidgetModel
import core.ui.widgets.CappedWidgetArea
import core.ui.widgets.HeaderModel
import core.ui.widgets.IAppWidgetViewCache
import core.ui.widgets.MediaPlayerUiState
import core.ui.widgets.WidgetAreaState
import core.ui.widgets.WidgetAreaUi
import core.ui.widgets.WidgetPlaceModel
import core.ui.widgets.createAppWidgetModel
import core.ui.widgets.createHeaderModel
import feature.launcher.createTestWidgetAreaUi
import io.mockk.clearMocks
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifySequence
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [TEST_SDK])
class WidgetAreaHapticTest {
    @get:Rule
    val composeRule: ComposeContentTestRule = createComposeRule()

    private lateinit var mockHapticFeedback: HapticFeedback
    private lateinit var mockAppWidgetViewCache: IAppWidgetViewCache

    @Before
    fun setUp() {
        mockHapticFeedback = mockk(relaxed = true)
        mockAppWidgetViewCache = mockk(relaxed = true)
        startKoin {
            modules(module { single<IAppWidgetViewCache> { mockAppWidgetViewCache } })
        }
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `an App Widget placeholder long-press plays one haptic and opens its menu`() {
        val mockViewModel: WidgetAreaViewModel = setAllMenuSurfacesContent(menuEnabled = true)

        longPress(composeRule.onAllNodesWithText(WIDGET_LABEL).onLast())

        verify(exactly = 1) { mockHapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress) }
        verify(exactly = 1) { mockViewModel.openAppWidgetMenu(widgetId = APP_WIDGET_ID) }
    }

    @Test
    fun `each Widget Area menu long-press plays one haptic and opens its menu`() {
        val mockViewModel: WidgetAreaViewModel = setAllMenuSurfacesContent(menuEnabled = true)

        longPressAllMenuSurfaces()

        verifySequence {
            mockHapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            mockViewModel.openHeaderMenu(widgetId = HEADER_WIDGET_ID)
            mockHapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            mockViewModel.openAppWidgetMenu(widgetId = APP_WIDGET_ID)
            mockHapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            mockViewModel.openMediaPlayerMenu()
            mockHapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            mockViewModel.openEmptyAreaMenu()
        }
    }

    @Test
    fun `Widget Area menu long-presses do nothing while menus are disabled`() {
        val mockViewModel: WidgetAreaViewModel = setAllMenuSurfacesContent(menuEnabled = false)

        longPressAllMenuSurfaces()

        verify(exactly = 0) { mockHapticFeedback.performHapticFeedback(any()) }
        verify(exactly = 0) { mockViewModel.openHeaderMenu(any()) }
        verify(exactly = 0) { mockViewModel.openAppWidgetMenu(any()) }
        verify(exactly = 0) { mockViewModel.openEmptyAreaMenu() }
        verify(exactly = 0) { mockViewModel.openMediaPlayerMenu() }
    }

    private fun setAllMenuSurfacesContent(menuEnabled: Boolean): WidgetAreaViewModel {
        val inputHeader: HeaderModel = createHeaderModel(id = HEADER_WIDGET_ID, timeText = HEADER_TIME_TEXT)
        val inputAppWidget: AppWidgetModel = createAppWidgetModel(
            id = APP_WIDGET_ID,
            availability = WidgetAvailability.Gone
        )
        val inputPlaces: List<WidgetPlaceModel> = listOf(
            WidgetPlaceModel(id = HEADER_PLACE_ID, heightDp = null, widgets = listOf(inputHeader)),
            WidgetPlaceModel(id = APP_WIDGET_PLACE_ID, heightDp = APP_WIDGET_HEIGHT_DP, widgets = listOf(inputAppWidget))
        )
        val inputMediaPlayer: MediaPlayerUiState = createMediaPlayerState()
        val inputWidgetAreaUi: WidgetAreaUi = createTestWidgetAreaUi(
            state = WidgetAreaState(
                places = inputPlaces,
                isLoaded = true,
                widgetSpacingDp = 0,
                listState = LazyListState()
            ),
            mediaPlayer = inputMediaPlayer
        )
        val mockViewModel: WidgetAreaViewModel = createWidgetAreaViewModelMock(inputWidgetAreaUi)
        composeRule.setContent {
            CompositionLocalProvider(LocalHapticFeedback provides mockHapticFeedback) {
                val widgetArea: WidgetAreaUi = rememberWidgetAreaUi(
                    viewModel = mockViewModel,
                    onLaunch = {},
                    onOpenAgenda = {},
                    menuEnabled = menuEnabled
                )
                val emptyWidgetArea: WidgetAreaUi = remember(widgetArea.actions) {
                    createTestWidgetAreaUi(
                        state = WidgetAreaState(isLoaded = true, listState = LazyListState()),
                        actions = widgetArea.actions
                    )
                }
                Column(modifier = Modifier.fillMaxWidth()) {
                    CappedWidgetArea(
                        ui = widgetArea,
                        maxHeight = POPULATED_WIDGET_AREA_MAX_HEIGHT,
                        railSide = RailSide.DEFAULT
                    )
                    CappedWidgetArea(
                        ui = emptyWidgetArea,
                        maxHeight = EMPTY_WIDGET_AREA_MAX_HEIGHT,
                        railSide = RailSide.DEFAULT
                    )
                }
            }
        }
        composeRule.waitForIdle()
        clearMocks(mockViewModel, mockHapticFeedback, answers = false)
        return mockViewModel
    }

    private fun longPressAllMenuSurfaces() {
        longPress(composeRule.onAllNodes(hasText(HEADER_TIME_TEXT), useUnmergedTree = true).onLast())
        longPress(composeRule.onAllNodesWithText(WIDGET_LABEL).onLast())
        longPress(composeRule.onNodeWithText(MEDIA_TITLE, useUnmergedTree = true))
        longPress(composeRule.onAllNodes(hasScrollAction()).onLast())
    }

    private fun longPress(target: SemanticsNodeInteraction) {
        target.performTouchInput { longClick() }
        composeRule.waitForIdle()
    }

    private fun createMediaPlayerState(): MediaPlayerUiState.Visible = MediaPlayerUiState.Visible(
        session = createMediaSessionSnapshot(
            key = "media-session",
            title = MEDIA_TITLE,
            artist = null,
            actions = setOf(MediaCommand.Pause)
        ),
        showArtwork = false
    )

    private companion object {
        const val HEADER_WIDGET_ID: Long = 1L
        const val HEADER_PLACE_ID: Long = 1L
        const val APP_WIDGET_ID: Long = 2L
        const val APP_WIDGET_PLACE_ID: Long = 2L
        const val APP_WIDGET_HEIGHT_DP: Int = 96
        const val HEADER_TIME_TEXT: String = "12:34"
        const val MEDIA_TITLE: String = "Morning"
        val POPULATED_WIDGET_AREA_MAX_HEIGHT: Dp = 320.dp
        val EMPTY_WIDGET_AREA_MAX_HEIGHT: Dp = 160.dp
    }
}
