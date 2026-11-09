package core.ui.popups

import app.cash.turbine.test
import core.data.icons.IIconRepository
import core.data.icons.model.CustomIcon
import core.data.icons.model.Icon
import core.data.icons.model.IconPack
import core.data.icons.model.IconPackCategory
import core.data.testing.TEST_SDK
import core.data.testing.createIconRepositoryMock
import io.mockk.coVerify
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
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
private const val CALENDAR_DRAWABLE_NAME: String = "calendar"
private const val GOOGLE_MAPS_DRAWABLE_NAME: String = "google_maps"
private const val GOOGLE_MAIL_DRAWABLE_NAME: String = "google_mail"

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
        drawableName = CALENDAR_DRAWABLE_NAME
    )
    private val inputGoogleMapsIcon: Icon.PackDrawableIcon = Icon.PackDrawableIcon(
        packPackageName = ICON_PACK_PACKAGE_NAME,
        drawableName = GOOGLE_MAPS_DRAWABLE_NAME
    )
    private val inputGoogleMailIcon: Icon.PackDrawableIcon = Icon.PackDrawableIcon(
        packPackageName = ICON_PACK_PACKAGE_NAME,
        drawableName = GOOGLE_MAIL_DRAWABLE_NAME
    )
    private val inputCategories: List<IconPackCategory> = listOf(
        IconPackCategory(name = "Calendar", icons = listOf(inputIcon, inputGoogleMapsIcon)),
        IconPackCategory(name = "Communication", icons = listOf(inputGoogleMailIcon))
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
    fun `opens a pack and holds its categories in pack order`() = runTest {
        val mockIconRepository: IIconRepository = createPackIconRepository()
        val picker: IIconPicker = createPackPicker(mockIconRepository = mockIconRepository)

        picker.iconPickerState.test {
            picker.startPicking(inputApp)
            picker.openPack(inputPack)
            runCurrent()
            val actualState: IconPickerState.Pack = expectMostRecentItem() as IconPickerState.Pack
            assertEquals(inputPack, actualState.iconPack)
            assertEquals(inputCategories, actualState.categories)
            assertEquals("", actualState.query)
            assertTrue(actualState.results.isEmpty())
        }
        coVerify(exactly = 1) { mockIconRepository.getPackCategories(ICON_PACK_PACKAGE_NAME) }
    }

    @Test
    fun `filters a pack by drawable name ignoring case and underscores`() = runTest {
        val picker: IIconPicker = createPackPicker()

        picker.iconPickerState.test {
            picker.startPicking(inputApp)
            picker.openPack(inputPack)
            runCurrent()
            picker.setQuery("GOOGLE Maps")
            runCurrent()
            val actualState: IconPickerState.Pack = expectMostRecentItem() as IconPickerState.Pack
            assertEquals("GOOGLE Maps", actualState.query)
            assertEquals(listOf(inputGoogleMapsIcon), actualState.results)
        }
    }

    @Test
    fun `flattens matching icons from every category into one result list`() = runTest {
        val picker: IIconPicker = createPackPicker()

        picker.iconPickerState.test {
            picker.startPicking(inputApp)
            picker.openPack(inputPack)
            runCurrent()
            picker.setQuery("google")
            runCurrent()
            val actualState: IconPickerState.Pack = expectMostRecentItem() as IconPickerState.Pack
            assertEquals(listOf(inputGoogleMapsIcon, inputGoogleMailIcon), actualState.results)
        }
    }

    @Test
    fun `clearing the query restores the grouped view`() = runTest {
        val picker: IIconPicker = createPackPicker()

        picker.iconPickerState.test {
            picker.startPicking(inputApp)
            picker.openPack(inputPack)
            picker.setQuery("google maps")
            runCurrent()
            picker.setQuery("")
            runCurrent()
            val actualState: IconPickerState.Pack = expectMostRecentItem() as IconPickerState.Pack
            assertEquals("", actualState.query)
            assertTrue(actualState.results.isEmpty())
            assertEquals(inputCategories, actualState.categories)
        }
    }

    @Test
    fun `streams Suggested icons into the open picker and applies them`() = runTest {
        val inputSuggestions: MutableStateFlow<List<Icon>> = MutableStateFlow(emptyList())
        val mockIconRepository: IIconRepository = createPackIconRepository(suggestedIcons = inputSuggestions)
        val picker: IIconPicker = createPackPicker(mockIconRepository = mockIconRepository)

        picker.iconPickerState.test {
            assertEquals(IconPickerState.Closed, awaitItem())
            picker.startPicking(inputApp)
            val actualInitial: IconPickerState.Listing = awaitItem() as IconPickerState.Listing
            assertTrue(actualInitial.suggestions.isEmpty())
            inputSuggestions.value = listOf(inputApp.defaultIcon, inputIcon)
            val actualSuggested: IconPickerState.Listing = awaitItem() as IconPickerState.Listing
            assertEquals(listOf(inputApp.defaultIcon, inputIcon), actualSuggested.suggestions)
            picker.selectIcon(inputIcon)
            verify(exactly = 1) {
                mockPreferencesRepository.setCustomIcon(
                    inputApp,
                    CustomIcon.PackDrawable(packPackageName = ICON_PACK_PACKAGE_NAME, drawableName = CALENDAR_DRAWABLE_NAME)
                )
            }
            assertEquals(IconPickerState.Closed, awaitItem())
        }
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
                CustomIcon.PackDrawable(packPackageName = ICON_PACK_PACKAGE_NAME, drawableName = CALENDAR_DRAWABLE_NAME)
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

    private fun createPackIconRepository(suggestedIcons: StateFlow<List<Icon>> = MutableStateFlow(emptyList())): IIconRepository =
        createIconRepositoryMock(
            inputIcon = inputApp.defaultIcon,
            iconPacks = iconPacks,
            packCategories = inputCategories,
            suggestedIcons = suggestedIcons
        )

    private fun TestScope.createPackPicker(mockIconRepository: IIconRepository = createPackIconRepository()): IIconPicker {
        setLaunchableGroups(groups = inputGroups)
        setIconPacks(packs = setOf(IconPack.SystemIconPack, inputPack))
        return createIconPicker(mockIconRepository = mockIconRepository)
    }
}
