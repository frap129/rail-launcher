package core.ui.popups

import app.cash.turbine.test
import core.data.icons.IIconRepository
import core.data.icons.model.CustomIcon
import core.data.icons.model.Icon
import core.data.icons.model.IconPack
import core.data.testing.TEST_SDK
import core.data.testing.createIconRepositoryMock
import io.mockk.coVerify
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val ICON_PACK_NAME: String = "Example pack"
private const val ICON_PACK_PACKAGE_NAME: String = "com.example.iconpack"
private const val DRAWABLE_NAME: String = "calendar"

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [TEST_SDK])
class IIconPickerImplTest : PopupTestBase() {
    private val inputPack: IconPack.CustomIconPack = IconPack.CustomIconPack(
        name = ICON_PACK_NAME,
        packageName = ICON_PACK_PACKAGE_NAME,
        icon = Icon.ApplicationIcon(ICON_PACK_PACKAGE_NAME)
    )
    private val inputIcon: Icon.PackDrawableIcon = Icon.PackDrawableIcon(
        packPackageName = ICON_PACK_PACKAGE_NAME,
        drawableName = DRAWABLE_NAME
    )

    @Test
    fun `lists Original and the installed icon packs when opened`() = runTest {
        setLaunchableGroups(groups = inputGroups)
        setIconPacks(packs = setOf(IconPack.SystemIconPack, inputPack))
        val picker: IIconPicker = createIconPicker()

        picker.iconPickerState.test {
            assertEquals(IconPickerState.Closed, expectMostRecentItem())
            picker.startPicking(inputApp)
            runCurrent()
            val actualState: IconPickerState.Listing = expectMostRecentItem() as IconPickerState.Listing
            assertEquals(inputApp, actualState.owner)
            assertEquals(listOf(inputPack), actualState.iconPacks)
        }
    }

    @Test
    fun `lists no icon packs when none are installed`() = runTest {
        setLaunchableGroups(groups = inputGroups)
        setIconPacks(packs = setOf(IconPack.SystemIconPack))
        val picker: IIconPicker = createIconPicker()

        picker.iconPickerState.test {
            picker.startPicking(inputApp)
            runCurrent()
            val actualState: IconPickerState.Listing = expectMostRecentItem() as IconPickerState.Listing
            assertTrue(actualState.iconPacks.isEmpty())
        }
    }

    @Test
    fun `opens a pack and holds its icons in memory`() = runTest {
        setLaunchableGroups(groups = inputGroups)
        setIconPacks(packs = setOf(IconPack.SystemIconPack, inputPack))
        val mockIconRepository: IIconRepository = createIconRepositoryMock(
            inputIcon = inputApp.defaultIcon,
            iconPacks = iconPacks,
            packIcons = listOf(inputIcon)
        )
        val picker: IIconPicker = createIconPicker(mockIconRepository = mockIconRepository)

        picker.iconPickerState.test {
            picker.startPicking(inputApp)
            picker.openPack(inputPack)
            runCurrent()
            val actualState: IconPickerState.Pack = expectMostRecentItem() as IconPickerState.Pack
            assertEquals(inputPack, actualState.iconPack)
            assertEquals(listOf(inputIcon), actualState.icons)
        }
        coVerify(exactly = 1) { mockIconRepository.getPackIcons(ICON_PACK_PACKAGE_NAME) }
    }

    @Test
    fun `applies a pack icon and closes the picker`() = runTest {
        setLaunchableGroups(groups = inputGroups)
        setIconPacks(packs = setOf(IconPack.SystemIconPack, inputPack))
        val picker: IIconPicker = createIconPicker()
        backgroundScope.launch { picker.iconPickerState.collect { } }
        picker.startPicking(inputApp)
        runCurrent()

        picker.selectIcon(inputIcon)

        verify(exactly = 1) {
            mockPreferencesRepository.setCustomIcon(
                inputApp,
                CustomIcon.PackDrawable(packPackageName = ICON_PACK_PACKAGE_NAME, drawableName = DRAWABLE_NAME)
            )
        }
        assertEquals(IconPickerState.Closed, picker.iconPickerState.value)
    }

    @Test
    fun `Original resets the Custom icon and closes the picker`() = runTest {
        setLaunchableGroups(groups = inputGroups)
        val picker: IIconPicker = createIconPicker()
        backgroundScope.launch { picker.iconPickerState.collect { } }
        picker.startPicking(inputApp)
        runCurrent()

        picker.resetIcon()

        verify(exactly = 1) { mockPreferencesRepository.resetCustomIcon(inputApp) }
        assertEquals(IconPickerState.Closed, picker.iconPickerState.value)
    }

    @Test
    fun `Back steps out of a pack then closes the picker`() = runTest {
        setLaunchableGroups(groups = inputGroups)
        setIconPacks(packs = setOf(IconPack.SystemIconPack, inputPack))
        val picker: IIconPicker = createIconPicker()
        backgroundScope.launch { picker.iconPickerState.collect { } }
        picker.startPicking(inputApp)
        picker.openPack(inputPack)
        runCurrent()

        assertTrue(picker.iconPickerState.value is IconPickerState.Pack)
        assertTrue(picker.backBeforeItemMenu())
        assertTrue(picker.iconPickerState.value is IconPickerState.Listing)
        assertTrue(picker.backBeforeItemMenu())
        assertEquals(IconPickerState.Closed, picker.iconPickerState.value)
        assertFalse(picker.backBeforeItemMenu())
    }
}
