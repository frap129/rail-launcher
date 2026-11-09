package core.data.prefs

import core.data.icons.model.CustomIcon
import core.data.icons.model.Icon
import core.data.icons.model.IconPack
import core.data.launchables.model.Launchable
import core.data.launchables.model.RailLaunchable
import core.data.room.dao.LaunchableCustomizationDao
import core.data.room.dao.SettingsDao
import core.data.testing.InMemoryDatabaseTest
import core.data.testing.TEST_CUSTOM_ICON_DRAWABLE_NAME
import core.data.testing.TEST_ICON_PACK_PACKAGE
import core.data.testing.TEST_RAIL_ICON_RES_ID
import core.data.testing.createCustomIconPack
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val RENAMED_ITEM_NAME: String = "Renamed Item"
private const val SECOND_RENAMED_ITEM_NAME: String = "Second Renamed Item"
private const val ORIGINAL_ITEM_NAME: String = "Original"
private const val LAUNCHABLE_DEEPLINK: String = "rail-launcher://test"
private val OTHER_CUSTOMIZATION: LaunchableCustomization = LaunchableCustomization(key = "other", name = "Other")
private val CUSTOM_ICON: CustomIcon = CustomIcon.PackDrawable(
    packPackageName = TEST_ICON_PACK_PACKAGE,
    drawableName = TEST_CUSTOM_ICON_DRAWABLE_NAME
)

class IPreferencesRepositoryImplTest : InMemoryDatabaseTest() {
    @Test
    fun `exposes launchable customizations from the DAO`() = databaseTest {
        val expectedCustomizations: List<LaunchableCustomization> = listOf(
            LaunchableCustomization(key = "first", name = "First"),
            LaunchableCustomization(key = "second", name = "Second")
        )
        val launchableCustomizationDao: LaunchableCustomizationDao = database.getLaunchableCustomizationDao()
        launchableCustomizationDao.setName(key = "first", name = "First")
        launchableCustomizationDao.setName(key = "second", name = "Second")
        val repository: IPreferencesRepository = createPreferencesRepository(launchableCustomizationDao = launchableCustomizationDao)
        val actualCustomizations: List<LaunchableCustomization> = repository.launchableCustomizations.first()
        assertEquals(
            expectedCustomizations.sortedBy(LaunchableCustomization::key),
            actualCustomizations.sortedBy(LaunchableCustomization::key)
        )
    }

    @Test
    fun `exposes settings from the DAO`() = databaseTest {
        val expectedSettings: Settings = Settings(iconPack = TEST_ICON_PACK_PACKAGE)
        val settingsDao: SettingsDao = database.getSettingsDao()
        settingsDao.upsert(expectedSettings)
        val repository: IPreferencesRepository = createPreferencesRepository(settingsDao = settingsDao)
        val actualSettings: Settings = repository.settings.first()
        assertEquals(expectedSettings, actualSettings)
    }

    @Test
    fun `stores a renamed launchable through the DAO`() = databaseTest {
        val repository: IPreferencesRepository = createPreferencesRepository()
        val inputItem: Launchable = createLaunchable()
        repository.setItemName(item = inputItem, name = RENAMED_ITEM_NAME)
        val actualCustomization: LaunchableCustomization = awaitCustomization(key = inputItem.key)
        assertEquals(LaunchableCustomization(key = inputItem.key, name = RENAMED_ITEM_NAME), actualCustomization)
    }

    @Test
    fun `renames an existing launchable customization in place`() = databaseTest {
        val inputItem: Launchable = createLaunchable()
        val launchableCustomizationDao: LaunchableCustomizationDao = database.getLaunchableCustomizationDao()
        launchableCustomizationDao.setName(key = inputItem.key, name = ORIGINAL_ITEM_NAME)
        launchableCustomizationDao.setName(key = OTHER_CUSTOMIZATION.key, name = requireNotNull(OTHER_CUSTOMIZATION.name))
        val repository: IPreferencesRepository = createPreferencesRepository(launchableCustomizationDao = launchableCustomizationDao)
        val expectedCustomizations: Set<LaunchableCustomization> = setOf(
            LaunchableCustomization(key = inputItem.key, name = RENAMED_ITEM_NAME),
            OTHER_CUSTOMIZATION
        )
        repository.setItemName(item = inputItem, name = RENAMED_ITEM_NAME)
        awaitRenamedCustomization(key = inputItem.key, previousName = ORIGINAL_ITEM_NAME)
        val actualCustomizations: List<LaunchableCustomization> = repository.launchableCustomizations.first()
        assertEquals(expectedCustomizations.size, actualCustomizations.size)
        assertEquals(expectedCustomizations, actualCustomizations.toSet())
    }

    @Test
    fun `applies repeated renames to the same launchable`() = databaseTest {
        val inputItem: Launchable = createLaunchable()
        val repository: IPreferencesRepository = createPreferencesRepository()
        repository.setItemName(item = inputItem, name = RENAMED_ITEM_NAME)
        awaitCustomization(key = inputItem.key)
        repository.setItemName(item = inputItem, name = SECOND_RENAMED_ITEM_NAME)
        awaitRenamedCustomization(key = inputItem.key, previousName = RENAMED_ITEM_NAME)
        val actualCustomization: LaunchableCustomization = repository.launchableCustomizations.first().single()
        assertEquals(SECOND_RENAMED_ITEM_NAME, actualCustomization.name)
    }

    @Test
    fun `a blank name resets the launchable to its default name`() = databaseTest {
        val inputItem: Launchable = createLaunchable()
        val launchableCustomizationDao: LaunchableCustomizationDao = database.getLaunchableCustomizationDao()
        launchableCustomizationDao.setName(key = inputItem.key, name = RENAMED_ITEM_NAME)
        launchableCustomizationDao.setName(key = OTHER_CUSTOMIZATION.key, name = requireNotNull(OTHER_CUSTOMIZATION.name))
        val repository: IPreferencesRepository = createPreferencesRepository(launchableCustomizationDao = launchableCustomizationDao)

        repository.setItemName(item = inputItem, name = "   ")

        val actualCustomizations: List<LaunchableCustomization> = customizations { stored ->
            stored.none { customization -> customization.key == inputItem.key }
        }
        assertEquals(listOf(OTHER_CUSTOMIZATION), actualCustomizations)
    }

    @Test
    fun `stores a Custom icon without a name`() = databaseTest {
        val repository: IPreferencesRepository = createPreferencesRepository()
        val inputItem: Launchable = createLaunchable()

        repository.setCustomIcon(item = inputItem, icon = CUSTOM_ICON)

        val actualCustomization: LaunchableCustomization = awaitCustomization(key = inputItem.key)
        assertEquals(LaunchableCustomization(key = inputItem.key, icon = CUSTOM_ICON), actualCustomization)
    }

    @Test
    fun `setting a Custom icon keeps the stored name`() = databaseTest {
        val inputItem: Launchable = createLaunchable()
        val launchableCustomizationDao: LaunchableCustomizationDao = database.getLaunchableCustomizationDao()
        launchableCustomizationDao.setName(key = inputItem.key, name = RENAMED_ITEM_NAME)
        val repository: IPreferencesRepository = createPreferencesRepository(launchableCustomizationDao = launchableCustomizationDao)

        repository.setCustomIcon(item = inputItem, icon = CUSTOM_ICON)

        val actualCustomization: LaunchableCustomization =
            awaitCustomization(key = inputItem.key) { customization -> customization.icon == CUSTOM_ICON }
        assertEquals(RENAMED_ITEM_NAME, actualCustomization.name)
    }

    @Test
    fun `resetting the name keeps the Custom icon`() = databaseTest {
        val inputItem: Launchable = createLaunchable()
        val launchableCustomizationDao: LaunchableCustomizationDao = database.getLaunchableCustomizationDao()
        launchableCustomizationDao.setName(key = inputItem.key, name = RENAMED_ITEM_NAME)
        launchableCustomizationDao.setIcon(key = inputItem.key, icon = CUSTOM_ICON)
        val repository: IPreferencesRepository = createPreferencesRepository(launchableCustomizationDao = launchableCustomizationDao)

        repository.resetItemName(item = inputItem)

        val actualCustomization: LaunchableCustomization =
            awaitCustomization(key = inputItem.key) { customization -> customization.name == null }
        assertEquals(CUSTOM_ICON, actualCustomization.icon)
    }

    @Test
    fun `resetting the Custom icon keeps the stored name`() = databaseTest {
        val inputItem: Launchable = createLaunchable()
        val launchableCustomizationDao: LaunchableCustomizationDao = database.getLaunchableCustomizationDao()
        launchableCustomizationDao.setName(key = inputItem.key, name = RENAMED_ITEM_NAME)
        launchableCustomizationDao.setIcon(key = inputItem.key, icon = CUSTOM_ICON)
        val repository: IPreferencesRepository = createPreferencesRepository(launchableCustomizationDao = launchableCustomizationDao)

        repository.resetCustomIcon(item = inputItem)

        val actualCustomization: LaunchableCustomization =
            awaitCustomization(key = inputItem.key) { customization -> customization.icon == null }
        assertEquals(RENAMED_ITEM_NAME, actualCustomization.name)
    }

    @Test
    fun `resetting the last customization removes its row`() = databaseTest {
        val inputItem: Launchable = createLaunchable()
        val launchableCustomizationDao: LaunchableCustomizationDao = database.getLaunchableCustomizationDao()
        launchableCustomizationDao.setIcon(key = inputItem.key, icon = CUSTOM_ICON)
        val repository: IPreferencesRepository = createPreferencesRepository(launchableCustomizationDao = launchableCustomizationDao)

        repository.resetCustomIcon(item = inputItem)

        assertTrue(customizations { stored -> stored.none { customization -> customization.key == inputItem.key } }.isEmpty())
    }

    @Test
    fun `a blank name keeps a row that has a Custom icon`() = databaseTest {
        val inputItem: Launchable = createLaunchable()
        val launchableCustomizationDao: LaunchableCustomizationDao = database.getLaunchableCustomizationDao()
        launchableCustomizationDao.setName(key = inputItem.key, name = RENAMED_ITEM_NAME)
        launchableCustomizationDao.setIcon(key = inputItem.key, icon = CUSTOM_ICON)
        val repository: IPreferencesRepository = createPreferencesRepository(launchableCustomizationDao = launchableCustomizationDao)

        repository.setItemName(item = inputItem, name = "   ")

        val actualCustomization: LaunchableCustomization =
            awaitCustomization(key = inputItem.key) { customization -> customization.name == null }
        assertEquals(CUSTOM_ICON, actualCustomization.icon)
    }

    @Test
    fun `stores the selected custom icon pack through the DAO`() = databaseTest {
        val settingsDao: SettingsDao = database.getSettingsDao()
        val repository: IPreferencesRepository = createPreferencesRepository(settingsDao = settingsDao)
        val inputIconPack: IconPack.CustomIconPack = createCustomIconPack()
        repository.setIconPack(iconPack = inputIconPack)
        val actualSettings: Settings = awaitSettings(settingsDao)
        assertEquals(Settings(iconPack = TEST_ICON_PACK_PACKAGE), actualSettings)
    }

    @Test
    fun `exposes the selected icon pack after it is stored`() = databaseTest {
        val repository: IPreferencesRepository = createPreferencesRepository()
        repository.setIconPack(iconPack = createCustomIconPack())
        awaitSettings(database.getSettingsDao())
        val actualSettings: Settings = repository.settings.first()
        assertEquals(Settings(iconPack = TEST_ICON_PACK_PACKAGE), actualSettings)
    }

    @Test
    fun `clears the stored icon pack when the system pack is selected`() = databaseTest {
        val settingsDao: SettingsDao = database.getSettingsDao()
        settingsDao.upsert(Settings(iconPack = TEST_ICON_PACK_PACKAGE))
        val repository: IPreferencesRepository = createPreferencesRepository(settingsDao = settingsDao)
        repository.setIconPack(iconPack = IconPack.SystemIconPack)
        val actualSettings: Settings = awaitSettings(settingsDao) { stored -> stored.iconPack != TEST_ICON_PACK_PACKAGE }
        assertEquals(Settings(iconPack = null), actualSettings)
    }

    @Test
    fun `stores the Private Space switch without clearing the icon pack`() = databaseTest {
        val settingsDao: SettingsDao = database.getSettingsDao()
        settingsDao.upsert(Settings(iconPack = TEST_ICON_PACK_PACKAGE))
        val repository: IPreferencesRepository = createPreferencesRepository(settingsDao = settingsDao)

        repository.setHidePrivateSpaceWhenLocked(true)

        val actualSettings: Settings = awaitSettings(settingsDao) { stored -> stored.hidePrivateSpaceWhenLocked }
        assertEquals(Settings(iconPack = TEST_ICON_PACK_PACKAGE, hidePrivateSpaceWhenLocked = true), actualSettings)
    }

    @Test
    fun `stores the Scrub edges without clearing the icon pack`() = databaseTest {
        val settingsDao: SettingsDao = database.getSettingsDao()
        settingsDao.upsert(Settings(iconPack = TEST_ICON_PACK_PACKAGE))
        val repository: IPreferencesRepository = createPreferencesRepository(settingsDao = settingsDao)

        repository.setScrubEdges(ScrubEdges.START)

        val actualSettings: Settings = awaitSettings(settingsDao) { stored -> stored.scrubEdges == ScrubEdges.START }
        assertEquals(Settings(iconPack = TEST_ICON_PACK_PACKAGE, scrubEdges = ScrubEdges.START), actualSettings)
    }

    @Test
    fun `exposes the stored Scrub edges`() = databaseTest {
        val settingsDao: SettingsDao = database.getSettingsDao()
        settingsDao.upsert(Settings(scrubEdges = ScrubEdges.END))
        val repository: IPreferencesRepository = createPreferencesRepository(settingsDao = settingsDao)

        val actualSettings: Settings = repository.settings.first()

        assertEquals(ScrubEdges.END, actualSettings.scrubEdges)
    }

    @Test
    fun `stores the Rail side without clearing the icon pack`() = databaseTest {
        val settingsDao: SettingsDao = database.getSettingsDao()
        settingsDao.upsert(Settings(iconPack = TEST_ICON_PACK_PACKAGE))
        val repository: IPreferencesRepository = createPreferencesRepository(settingsDao = settingsDao)

        repository.setRailSide(RailSide.START)

        val actualSettings: Settings = awaitSettings(settingsDao) { stored -> stored.railSide == RailSide.START }
        assertEquals(Settings(iconPack = TEST_ICON_PACK_PACKAGE, railSide = RailSide.START), actualSettings)
    }

    @Test
    fun `exposes the stored Rail side`() = databaseTest {
        val settingsDao: SettingsDao = database.getSettingsDao()
        settingsDao.upsert(Settings(railSide = RailSide.START))
        val repository: IPreferencesRepository = createPreferencesRepository(settingsDao = settingsDao)

        val actualSettings: Settings = repository.settings.first()

        assertEquals(RailSide.START, actualSettings.railSide)
    }

    private suspend fun awaitCustomization(
        key: String,
        predicate: (LaunchableCustomization) -> Boolean = { true }
    ): LaunchableCustomization = customizations { stored ->
        stored.any { customization -> customization.key == key && predicate(customization) }
    }.single { customization -> customization.key == key }

    private suspend fun awaitRenamedCustomization(key: String, previousName: String): LaunchableCustomization = customizations { stored ->
        stored.any { customization -> customization.key == key && customization.name != previousName }
    }.single { customization -> customization.key == key }

    private suspend fun awaitSettings(settingsDao: SettingsDao, predicate: (Settings) -> Boolean = { true }): Settings =
        requireNotNull(settingsDao.observeRow().first { row -> row != null && predicate(row) })

    private fun createLaunchable(): Launchable = RailLaunchable(
        defaultName = "Original Item",
        defaultIcon = Icon.RailIcon(resId = TEST_RAIL_ICON_RES_ID),
        deeplink = LAUNCHABLE_DEEPLINK
    )
}
