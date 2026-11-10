package core.ui.popups

import android.app.Dialog
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import core.data.icons.model.Icon
import core.data.icons.model.IconPack
import core.data.icons.model.IconPackCategory
import core.data.testing.TEST_SDK
import core.data.testing.createApp
import core.data.testing.createCustomIconPack
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

private const val SOCIAL_MEDIA_DRAWABLE_NAME: String = "social_media"
private const val SOCIAL_MEDIA_LABEL: String = "social media"

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [TEST_SDK])
class IconPickerSheetTest {
    @get:Rule
    val composeRule: ComposeContentTestRule = createComposeRule()

    private val inputPack: IconPack.CustomIconPack = createCustomIconPack()
    private val inputIcon: Icon.PackDrawableIcon = Icon.PackDrawableIcon(
        packPackageName = inputPack.packageName,
        drawableName = SOCIAL_MEDIA_DRAWABLE_NAME
    )

    @Test
    fun `reads a pack icon's underscores as spaces`() {
        setPickerContent(
            state = IconPickerState.Pack(
                iconPack = inputPack,
                categories = listOf(IconPackCategory(name = "Social", icons = listOf(inputIcon)))
            )
        )

        composeRule.onNodeWithContentDescription(SOCIAL_MEDIA_LABEL).assertExists()
    }

    @Test
    fun `reads a Suggested icon's underscores as spaces`() {
        setPickerContent(
            state = IconPickerState.Listing(
                owner = createApp(),
                iconPacks = emptyList(),
                suggestions = listOf(inputIcon)
            )
        )

        composeRule.onNodeWithContentDescription(SOCIAL_MEDIA_LABEL).assertExists()
    }

    @Test
    fun `steps out of a pack on system Back`() {
        val mockIconPicker: IIconPicker = setPickerContent(
            state = IconPickerState.Pack(
                iconPack = inputPack,
                categories = listOf(IconPackCategory(name = "Social", icons = listOf(inputIcon)))
            )
        )

        val dialog: Dialog = requireNotNull(ShadowDialog.getLatestDialog())
        dialog.onBackPressed()

        verify(exactly = 1) { mockIconPicker.backBeforeItemMenu() }
        verify(exactly = 0) { mockIconPicker.cancelPicking() }
    }

    @Test
    fun `closes the picker when the pack sheet is swiped away`() {
        val mockIconPicker: IIconPicker = setPickerContent(
            state = IconPickerState.Pack(
                iconPack = inputPack,
                categories = listOf(IconPackCategory(name = "Social", icons = listOf(inputIcon)))
            )
        )

        composeRule.onNodeWithText(inputPack.name).performTouchInput { swipeDown() }
        composeRule.waitForIdle()

        verify(exactly = 1) { mockIconPicker.cancelPicking() }
        verify(exactly = 0) { mockIconPicker.backBeforeItemMenu() }
    }

    private fun setPickerContent(state: IconPickerState): IIconPicker {
        val mockIconPicker: IIconPicker = mockk(relaxed = true)
        every { mockIconPicker.iconPickerState } returns MutableStateFlow(state)
        composeRule.setContent { IconPickerHost(iconPicker = mockIconPicker) }
        return mockIconPicker
    }
}
