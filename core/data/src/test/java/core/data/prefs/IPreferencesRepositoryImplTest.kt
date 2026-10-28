package core.data.prefs

import core.data.icons.model.Icon
import core.data.icons.model.IconPack
import core.data.launchables.model.Launchable
import core.data.launchables.model.RailLaunchable
import core.data.room.dao.LaunchableCustomizationDao
import core.data.room.dao.SettingsDao
import core.data.testing.InMemoryDatabaseTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Test

private const val CUSTOM_ICON_PACK_NAME: String = "Custom Pack"
private const val CUSTOM_ICON_PACK_PACKAGE: String = "com.example.iconpack"
private const val RENAMED_ITEM_NAME: String = "Renamed Item"
private const val SECOND_RENAMED_ITEM_NAME: String = "Second Renamed Item"
private const val ORIGINAL_ITEM_NAME: String = "Original"
private const val RAIL_ICON_RES_ID: Int = 0
private const val LAUNCHABLE_DEEPLINK: String = "rail-launcher://test"
private val OTHER_CUSTOMIZATION: LaunchableCustomization = LaunchableCustomization(key = "other", name = "Other")

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
        val repository: IPreferencesRepository = createRepository(launchableCustomizationDao = launchableCustomizationDao)
        val actualCustomizations: List<LaunchableCustomization> = repository.launchableCustomizations.first()
        assertEquals(
            expectedCustomizations.sortedBy(LaunchableCustomization::key),
            actualCustomizations.sortedBy(LaunchableCustomization::key)
        )
    }

    @Test
    fun `exposes settings from the DAO`() = databaseTest {
        val expectedSettings: Settings = Settings(iconPack = CUSTOM_ICON_PACK_PACKAGE)
        val settingsDao: SettingsDao = database.getSettingsDao()
        settingsDao.upsert(expectedSettings)
        val repository: IPreferencesRepository = createRepository(settingsDao = settingsDao)
        val actualSettings: Settings = repository.settings.first()
        assertEquals(expectedSettings, actualSettings)
    }

    @Test
    fun `stores a renamed launchable through the DAO`() = databaseTest {
        val repository: IPreferencesRepository = createRepository()
        val inputItem: Launchable = createLaunchable()
        repository.setItemName(item = inputItem, name = RENAMED_ITEM_NAME)
        val actualCustomization: LaunchableCustomization = awaitNewCustomization(key = inputItem.key)
        assertEquals(LaunchableCustomization(key = inputItem.key, name = RENAMED_ITEM_NAME), actualCustomization)
    }

    @Test
    fun `renames an existing launchable customization in place`() = databaseTest {
        val inputItem: Launchable = createLaunchable()
        val launchableCustomizationDao: LaunchableCustomizationDao = database.getLaunchableCustomizationDao()
        launchableCustomizationDao.setName(key = inputItem.key, name = ORIGINAL_ITEM_NAME)
        launchableCustomizationDao.setName(key = OTHER_CUSTOMIZATION.key, name = requireNotNull(OTHER_CUSTOMIZATION.name))
        val repository: IPreferencesRepository = createRepository(launchableCustomizationDao = launchableCustomizationDao)
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
        val repository: IPreferencesRepository = createRepository()
        repository.setItemName(item = inputItem, name = RENAMED_ITEM_NAME)
        awaitNewCustomization(key = inputItem.key)
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
        val repository: IPreferencesRepository = createRepository(launchableCustomizationDao = launchableCustomizationDao)

        repository.setItemName(item = inputItem, name = "   ")

        val actualCustomizations: List<LaunchableCustomization> = customizations { stored ->
            stored.none { customization -> customization.key == inputItem.key }
        }
        assertEquals(listOf(OTHER_CUSTOMIZATION), actualCustomizations)
    }

    @Test
    fun `stores the selected custom icon pack through the DAO`() = databaseTest {
        val settingsDao: SettingsDao = database.getSettingsDao()
        val repository: IPreferencesRepository = createRepository(settingsDao = settingsDao)
        val inputIconPack: IconPack = createCustomIconPack()
        repository.setIconPack(iconPack = inputIconPack)
        val actualSettings: Settings = awaitStoredSettings(settingsDao)
        assertEquals(Settings(iconPack = CUSTOM_ICON_PACK_PACKAGE), actualSettings)
    }

    @Test
    fun `exposes the selected icon pack after it is stored`() = databaseTest {
        val repository: IPreferencesRepository = createRepository()
        repository.setIconPack(iconPack = createCustomIconPack())
        awaitStoredSettings(database.getSettingsDao())
        val actualSettings: Settings = repository.settings.first()
        assertEquals(Settings(iconPack = CUSTOM_ICON_PACK_PACKAGE), actualSettings)
    }

    @Test
    fun `clears the stored icon pack when the system pack is selected`() = databaseTest {
        val settingsDao: SettingsDao = database.getSettingsDao()
        settingsDao.upsert(Settings(iconPack = CUSTOM_ICON_PACK_PACKAGE))
        val repository: IPreferencesRepository = createRepository(settingsDao = settingsDao)
        repository.setIconPack(iconPack = IconPack.SystemIconPack)
        val actualSettings: Settings = awaitUpdatedSettings(settingsDao = settingsDao, previousIconPack = CUSTOM_ICON_PACK_PACKAGE)
        assertEquals(Settings(iconPack = null), actualSettings)
    }

    private fun TestScope.createRepository(
        launchableCustomizationDao: LaunchableCustomizationDao = database.getLaunchableCustomizationDao(),
        settingsDao: SettingsDao = database.getSettingsDao()
    ): IPreferencesRepository = IPreferencesRepositoryImpl(
        launchableCustomizationDao = launchableCustomizationDao,
        settingsDao = settingsDao,
        scope = backgroundScope
    )

    private suspend fun awaitNewCustomization(key: String): LaunchableCustomization =
        customizations { stored -> stored.any { customization -> customization.key == key } }
            .single { customization -> customization.key == key }

    private suspend fun awaitRenamedCustomization(key: String, previousName: String): LaunchableCustomization = customizations { stored ->
        stored.any { customization -> customization.key == key && customization.name != previousName }
    }.single { customization -> customization.key == key }

    private suspend fun awaitStoredSettings(settingsDao: SettingsDao): Settings =
        requireNotNull(settingsDao.observeRow().first { row -> row != null })

    private suspend fun awaitUpdatedSettings(settingsDao: SettingsDao, previousIconPack: String): Settings =
        requireNotNull(settingsDao.observeRow().first { row -> row != null && row.iconPack != previousIconPack })

    private fun createLaunchable(): Launchable = RailLaunchable(
        defaultName = "Original Item",
        defaultIcon = Icon.RailIcon(resId = RAIL_ICON_RES_ID),
        deeplink = LAUNCHABLE_DEEPLINK
    )

    private fun createCustomIconPack(): IconPack = IconPack.CustomIconPack(
        name = CUSTOM_ICON_PACK_NAME,
        packageName = CUSTOM_ICON_PACK_PACKAGE,
        icon = Icon.ApplicationIcon(CUSTOM_ICON_PACK_PACKAGE)
    )
}
