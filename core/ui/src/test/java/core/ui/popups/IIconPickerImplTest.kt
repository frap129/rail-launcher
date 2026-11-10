package core.ui.popups

import android.net.Uri
import app.cash.turbine.test
import core.data.icons.IIconImageImporter
import core.data.icons.IIconRepository
import core.data.icons.model.CustomIcon
import core.data.icons.model.Icon
import core.data.icons.model.IconPack
import core.data.icons.model.IconPackCategory
import core.data.testing.TEST_GALLERY_ICON_FILE_NAME
import core.data.testing.TEST_GALLERY_URI
import core.data.testing.TEST_SDK
import core.data.testing.createIconImageImporterMock
import core.data.testing.createIconRepositoryMock
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
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
        val picker: IIconPicker = createIconPickerFixture(mockIconRepository = mockIconRepository)

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
        val picker: IIconPicker = createIconPickerFixture()

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
        val picker: IIconPicker = createIconPickerFixture()

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
        val picker: IIconPicker = createIconPickerFixture()

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
        val picker: IIconPicker = createIconPickerFixture(mockIconRepository = mockIconRepository)

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
    fun `applies a gallery image and closes the picker`() = runTest {
        val mockImageImporter: IIconImageImporter = createIconImageImporterMock(fileName = TEST_GALLERY_ICON_FILE_NAME)
        val picker: IIconPicker = createIconPickerFixture(mockImageImporter = mockImageImporter, isOpen = true)

        picker.selectGalleryImage(uri = Uri.parse(TEST_GALLERY_URI))
        runCurrent()

        verify(exactly = 1) {
            mockPreferencesRepository.setCustomIcon(inputApp, CustomIcon.Image(fileName = TEST_GALLERY_ICON_FILE_NAME))
        }
        assertEquals(IconPickerState.Closed, picker.iconPickerState.value)
    }

    @Test
    fun `discards a gallery image when the picker closes before the import finishes`() = runTest {
        val inputImport: DeferredImageImport = DeferredImageImport()
        val picker: IIconPicker = createIconPickerFixture(mockImageImporter = inputImport.mockImageImporter, isOpen = true)

        picker.selectGalleryImage(uri = Uri.parse(TEST_GALLERY_URI))
        picker.cancelPicking()
        inputImport.inputFileName.complete(TEST_GALLERY_ICON_FILE_NAME)
        advanceUntilIdle()

        verify(exactly = 1) { inputImport.mockImageImporter.deleteImage(fileName = TEST_GALLERY_ICON_FILE_NAME) }
        verify(exactly = 0) { mockPreferencesRepository.setCustomIcon(any(), any()) }
        assertEquals(IconPickerState.Closed, picker.iconPickerState.value)
    }

    @Test
    fun `discards a gallery image when the card closes before the import finishes`() = runTest {
        val inputImport: DeferredImageImport = DeferredImageImport()
        val picker: IIconPicker = createIconPickerFixture(mockImageImporter = inputImport.mockImageImporter, isOpen = true)

        picker.selectGalleryImage(uri = Uri.parse(TEST_GALLERY_URI))
        picker.onCardClosed()
        inputImport.inputFileName.complete(TEST_GALLERY_ICON_FILE_NAME)
        advanceUntilIdle()

        verify(exactly = 1) { inputImport.mockImageImporter.deleteImage(fileName = TEST_GALLERY_ICON_FILE_NAME) }
        verify(exactly = 0) { mockPreferencesRepository.setCustomIcon(any(), any()) }
        assertEquals(IconPickerState.Closed, picker.iconPickerState.value)
    }

    @Test
    fun `ignores a second gallery result while the first image imports`() = runTest {
        val inputImport: DeferredImageImport = DeferredImageImport()
        val picker: IIconPicker = createIconPickerFixture(mockImageImporter = inputImport.mockImageImporter, isOpen = true)

        picker.startGalleryPick()
        picker.selectGalleryImage(uri = Uri.parse(TEST_GALLERY_URI))
        picker.startGalleryPick()
        picker.selectGalleryImage(uri = Uri.parse(TEST_GALLERY_URI))
        inputImport.inputFileName.complete(TEST_GALLERY_ICON_FILE_NAME)
        advanceUntilIdle()

        coVerify(exactly = 1) { inputImport.mockImageImporter.importImage(Uri.parse(TEST_GALLERY_URI)) }
        verify(exactly = 1) {
            mockPreferencesRepository.setCustomIcon(inputApp, CustomIcon.Image(fileName = TEST_GALLERY_ICON_FILE_NAME))
        }
        verify(exactly = 0) { inputImport.mockImageImporter.deleteImage(any()) }
        assertEquals(IconPickerState.Closed, picker.iconPickerState.value)
    }

    @Test
    fun `ignores a cancelled gallery result while an image imports`() = runTest {
        val inputImport: DeferredImageImport = DeferredImageImport()
        val picker: IIconPicker = createIconPickerFixture(mockImageImporter = inputImport.mockImageImporter, isOpen = true)

        picker.startGalleryPick()
        picker.selectGalleryImage(uri = Uri.parse(TEST_GALLERY_URI))
        picker.startGalleryPick()
        picker.selectGalleryImage(uri = null)
        inputImport.inputFileName.complete(TEST_GALLERY_ICON_FILE_NAME)
        advanceUntilIdle()

        verify(exactly = 1) {
            mockPreferencesRepository.setCustomIcon(inputApp, CustomIcon.Image(fileName = TEST_GALLERY_ICON_FILE_NAME))
        }
        verify(exactly = 0) { inputImport.mockImageImporter.deleteImage(any()) }
        assertEquals(IconPickerState.Closed, picker.iconPickerState.value)
    }

    @Test
    fun `opening a pack during an import discards the image and clears the spinner`() = runTest {
        val inputImport: DeferredImageImport = DeferredImageImport()
        val picker: IIconPicker = createIconPickerFixture(
            mockImageImporter = inputImport.mockImageImporter,
            isOpen = true
        )

        picker.selectGalleryImage(uri = Uri.parse(TEST_GALLERY_URI))
        picker.openPack(inputPack)
        runCurrent()
        assertTrue(picker.backBeforeItemMenu())
        runCurrent()

        val actualState: IconPickerState.Listing = picker.iconPickerState.value as IconPickerState.Listing
        assertFalse(actualState.isImportingImage)
        inputImport.inputFileName.complete(TEST_GALLERY_ICON_FILE_NAME)
        advanceUntilIdle()
        verify(exactly = 1) { inputImport.mockImageImporter.deleteImage(fileName = TEST_GALLERY_ICON_FILE_NAME) }
        verify(exactly = 0) { mockPreferencesRepository.setCustomIcon(any(), any()) }
    }

    @Test
    fun `keeps the picker open when the host stops during a gallery pick`() = runTest {
        val mockImageImporter: IIconImageImporter = createIconImageImporterMock(fileName = TEST_GALLERY_ICON_FILE_NAME)
        val picker: IIconPicker = createIconPickerFixture(mockImageImporter = mockImageImporter, isOpen = true)

        picker.startGalleryPick()
        picker.onHostStopped()
        runCurrent()

        val actualOpenState: IconPickerState.Listing = picker.iconPickerState.value as IconPickerState.Listing
        assertEquals(inputApp, actualOpenState.owner)
        picker.selectGalleryImage(uri = Uri.parse(TEST_GALLERY_URI))
        runCurrent()

        verify(exactly = 1) {
            mockPreferencesRepository.setCustomIcon(inputApp, CustomIcon.Image(fileName = TEST_GALLERY_ICON_FILE_NAME))
        }
        assertEquals(IconPickerState.Closed, picker.iconPickerState.value)
    }

    @Test
    fun `closes the picker when the card closes during a pending gallery pick`() = runTest {
        val picker: IIconPicker = createIconPickerFixture(isOpen = true)

        picker.startGalleryPick()
        picker.onCardClosed()

        assertEquals(IconPickerState.Closed, picker.iconPickerState.value)
    }

    @Test
    fun `keeps the picker open when the host stops again during a gallery pick`() = runTest {
        val picker: IIconPicker = createIconPickerFixture(isOpen = true)

        picker.startGalleryPick()
        picker.onHostStopped()
        picker.onHostStopped()

        assertTrue(picker.iconPickerState.value is IconPickerState.Listing)
    }

    @Test
    fun `clears the pending gallery pick when the picker returns no image`() = runTest {
        val mockImageImporter: IIconImageImporter = createIconImageImporterMock(fileName = TEST_GALLERY_ICON_FILE_NAME)
        val picker: IIconPicker = createIconPickerFixture(mockImageImporter = mockImageImporter, isOpen = true)

        picker.startGalleryPick()
        picker.selectGalleryImage(uri = null)
        runCurrent()
        assertTrue(picker.iconPickerState.value is IconPickerState.Listing)

        picker.onHostStopped()

        assertEquals(IconPickerState.Closed, picker.iconPickerState.value)
        coVerify(exactly = 0) { mockImageImporter.importImage(any()) }
        verify(exactly = 0) { mockPreferencesRepository.setCustomIcon(any(), any()) }
    }

    @Test
    fun `shows an error and applies nothing when a gallery image fails`() = runTest {
        setLaunchableGroups(groups = inputGroups)
        val mockImageImporter = createIconImageImporterMock(fileName = null)
        val picker: IIconPicker = createIconPicker(mockImageImporter = mockImageImporter)
        backgroundScope.launch { picker.iconPickerState.collect { } }
        picker.startPicking(inputApp)
        runCurrent()

        picker.selectGalleryImage(uri = Uri.parse(TEST_GALLERY_URI))

        runCurrent()
        verify(exactly = 0) { mockPreferencesRepository.setCustomIcon(any(), any()) }
        val actualState: IconPickerState.Listing = picker.iconPickerState.value as IconPickerState.Listing
        assertFalse(actualState.isImportingImage)
        assertTrue(actualState.imageImportFailed)
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

    private fun TestScope.createIconPickerFixture(
        mockIconRepository: IIconRepository = createPackIconRepository(),
        mockImageImporter: IIconImageImporter = createIconImageImporterMock(),
        inputIconPacks: Set<IconPack> = setOf(IconPack.SystemIconPack, inputPack),
        isOpen: Boolean = false
    ): IIconPicker {
        setLaunchableGroups(groups = inputGroups)
        setIconPacks(packs = inputIconPacks)
        val picker: IIconPicker = createIconPicker(mockIconRepository = mockIconRepository, mockImageImporter = mockImageImporter)
        if (!isOpen) {
            return picker
        }
        backgroundScope.launch { picker.iconPickerState.collect { } }
        picker.startPicking(inputApp)
        runCurrent()
        return picker
    }

    private fun createPackIconRepository(suggestedIcons: StateFlow<List<Icon>> = MutableStateFlow(emptyList())): IIconRepository =
        createIconRepositoryMock(
            inputIcon = inputApp.defaultIcon,
            iconPacks = iconPacks,
            packCategories = inputCategories,
            suggestedIcons = suggestedIcons
        )
}

private class DeferredImageImport {
    val inputFileName: CompletableDeferred<String?> = CompletableDeferred()
    val mockImageImporter: IIconImageImporter = createIconImageImporterMock().apply {
        coEvery { importImage(any()) } coAnswers { inputFileName.await() }
    }
}
