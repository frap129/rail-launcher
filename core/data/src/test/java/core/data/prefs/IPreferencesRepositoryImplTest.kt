package core.data.prefs

import core.data.icons.model.Icon
import core.data.icons.model.IconPack
import core.data.launchables.model.Launchable
import core.data.launchables.model.RailLaunchable
import core.data.room.dao.LaunchableCustomizationDao
import core.data.room.dao.SettingsDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test

private const val WRITE_TIMEOUT_MS: Long = 5_000
private const val CUSTOM_ICON_PACK_NAME: String = "Custom Pack"
private const val CUSTOM_ICON_PACK_PACKAGE: String = "com.example.iconpack"
private const val RENAMED_ITEM_NAME: String = "Renamed Item"
private const val SECOND_RENAMED_ITEM_NAME: String = "Second Renamed Item"
private const val RAIL_ICON_RES_ID: Int = 0
private const val LAUNCHABLE_DEEPLINK: String = "rail-launcher://test"
private val OTHER_CUSTOMIZATION: LaunchableCustomization = LaunchableCustomization(key = "other", name = "Other")

class IPreferencesRepositoryImplTest {
    @Test
    fun `exposes launchable customizations from the DAO`() = runTest {
        val expected: List<LaunchableCustomization> = listOf(
            LaunchableCustomization(key = "first", name = "First"),
            LaunchableCustomization(key = "second", name = "Second")
        )
        val repository: IPreferencesRepository = createRepository(inputCustomizations = expected)
        val actual: List<LaunchableCustomization> = repository.launchableCustomizations.first()
        assertEquals(expected, actual)
    }

    @Test
    fun `exposes settings from the DAO`() = runTest {
        val expected: Settings = Settings(iconPack = CUSTOM_ICON_PACK_PACKAGE)
        val repository: IPreferencesRepository = createRepository(inputSettings = expected)
        val actual: Settings = repository.settings.first()
        assertEquals(expected, actual)
    }

    @Test
    fun `stores a renamed launchable through the DAO`() = runTest {
        val mockLaunchableCustomizationDao: FakeLaunchableCustomizationDao = FakeLaunchableCustomizationDao()
        val repository: IPreferencesRepository = createRepository(
            mockLaunchableCustomizationDao = mockLaunchableCustomizationDao
        )
        val inputItem: Launchable = createLaunchable()
        repository.setItemName(item = inputItem, name = RENAMED_ITEM_NAME)
        val actual: LaunchableRename = mockLaunchableCustomizationDao.awaitRename()
        assertEquals(inputItem.key, actual.key)
        assertEquals(RENAMED_ITEM_NAME, actual.name)
    }

    @Test
    fun `renames an existing launchable customization in place`() = runTest {
        val inputItem: Launchable = createLaunchable()
        val mockLaunchableCustomizationDao: FakeLaunchableCustomizationDao = FakeLaunchableCustomizationDao(
            inputCustomizations = listOf(LaunchableCustomization(key = inputItem.key), OTHER_CUSTOMIZATION)
        )
        val repository: IPreferencesRepository = createRepository(
            mockLaunchableCustomizationDao = mockLaunchableCustomizationDao
        )
        val expectedCustomizations: Set<LaunchableCustomization> = setOf(
            LaunchableCustomization(key = inputItem.key, name = RENAMED_ITEM_NAME),
            OTHER_CUSTOMIZATION
        )
        repository.setItemName(item = inputItem, name = RENAMED_ITEM_NAME)
        mockLaunchableCustomizationDao.awaitRename()
        val actualCustomizations: List<LaunchableCustomization> = repository.launchableCustomizations.first()
        assertEquals(expectedCustomizations.size, actualCustomizations.size)
        assertEquals(expectedCustomizations, actualCustomizations.toSet())
    }

    @Test
    fun `applies repeated renames to the same launchable`() = runTest {
        val inputItem: Launchable = createLaunchable()
        val mockLaunchableCustomizationDao: FakeLaunchableCustomizationDao = FakeLaunchableCustomizationDao()
        val repository: IPreferencesRepository = createRepository(
            mockLaunchableCustomizationDao = mockLaunchableCustomizationDao
        )
        repository.setItemName(item = inputItem, name = RENAMED_ITEM_NAME)
        mockLaunchableCustomizationDao.awaitRename()
        repository.setItemName(item = inputItem, name = SECOND_RENAMED_ITEM_NAME)
        mockLaunchableCustomizationDao.awaitRename()
        val actual: LaunchableCustomization = repository.launchableCustomizations.first().single()
        assertEquals(SECOND_RENAMED_ITEM_NAME, actual.name)
    }

    @Test
    fun `stores the selected custom icon pack through the DAO`() = runTest {
        val mockSettingsDao: FakeSettingsDao = FakeSettingsDao()
        val repository: IPreferencesRepository = createRepository(mockSettingsDao = mockSettingsDao)
        val inputIconPack: IconPack = createCustomIconPack()
        repository.setIconPack(iconPack = inputIconPack)
        val actual: Settings = mockSettingsDao.awaitStoredSettings()
        assertEquals(Settings(iconPack = CUSTOM_ICON_PACK_PACKAGE), actual)
    }

    @Test
    fun `exposes the selected icon pack after it is stored`() = runTest {
        val mockSettingsDao: FakeSettingsDao = FakeSettingsDao()
        val repository: IPreferencesRepository = createRepository(mockSettingsDao = mockSettingsDao)
        repository.setIconPack(iconPack = createCustomIconPack())
        mockSettingsDao.awaitStoredSettings()
        val actual: Settings = repository.settings.first()
        assertEquals(Settings(iconPack = CUSTOM_ICON_PACK_PACKAGE), actual)
    }

    @Test
    fun `clears the stored icon pack when the system pack is selected`() = runTest {
        val mockSettingsDao: FakeSettingsDao = FakeSettingsDao()
        val repository: IPreferencesRepository = createRepository(mockSettingsDao = mockSettingsDao)
        repository.setIconPack(iconPack = IconPack.SystemIconPack)
        val actual: Settings = mockSettingsDao.awaitStoredSettings()
        assertEquals(Settings(iconPack = null), actual)
    }

    private fun TestScope.createRepository(
        inputCustomizations: List<LaunchableCustomization> = emptyList(),
        inputSettings: Settings = Settings(),
        mockLaunchableCustomizationDao: FakeLaunchableCustomizationDao = FakeLaunchableCustomizationDao(inputCustomizations),
        mockSettingsDao: FakeSettingsDao = FakeSettingsDao(inputSettings)
    ): IPreferencesRepository = IPreferencesRepositoryImpl(
        launchableCustomizationDao = mockLaunchableCustomizationDao,
        settingsDao = mockSettingsDao,
        scope = backgroundScope
    )

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

private data class LaunchableRename(val key: String, val name: String)

private class FakeLaunchableCustomizationDao(inputCustomizations: List<LaunchableCustomization> = emptyList()) :
    LaunchableCustomizationDao {
    private val customizations: MutableStateFlow<List<LaunchableCustomization>> = MutableStateFlow(inputCustomizations)
    private val renameSignals: Channel<Unit> = Channel(Channel.UNLIMITED)
    private val recordedRenames: MutableList<LaunchableRename> = mutableListOf()

    override fun observeAll(): Flow<List<LaunchableCustomization>> = customizations

    override suspend fun setName(key: String, name: String) {
        recordedRenames.add(LaunchableRename(key = key, name = name))
        val existing: LaunchableCustomization? = customizations.value.find { it.key == key }
        val updated: LaunchableCustomization = (existing ?: LaunchableCustomization(key = key)).copy(name = name)
        customizations.value = customizations.value.filterNot { it.key == key } + updated
        renameSignals.send(Unit)
    }

    suspend fun awaitRename(): LaunchableRename = withContext(Dispatchers.Default) {
        withTimeout(WRITE_TIMEOUT_MS) { renameSignals.receive() }
        recordedRenames.last()
    }
}

private class FakeSettingsDao(inputSettings: Settings = Settings()) : SettingsDao() {
    private val settingsFlow: MutableStateFlow<Settings> = MutableStateFlow(inputSettings)
    private val writeSignals: Channel<Unit> = Channel(Channel.UNLIMITED)
    private val recordedSettings: MutableList<Settings> = mutableListOf()

    override fun observeRow(): Flow<Settings?> = settingsFlow

    override fun observe(): Flow<Settings> = settingsFlow

    override suspend fun upsert(settings: Settings) {
        recordedSettings.add(settings)
        settingsFlow.value = settings
        writeSignals.send(Unit)
    }

    suspend fun awaitStoredSettings(): Settings = withContext(Dispatchers.Default) {
        withTimeout(WRITE_TIMEOUT_MS) { writeSignals.receive() }
        recordedSettings.last()
    }
}
