package core.data.room.dao

import core.data.prefs.Settings
import core.data.testing.InMemoryDatabaseTest
import core.data.testing.TEST_ICON_PACK_PACKAGE
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsDaoTest : InMemoryDatabaseTest() {
    @Test
    fun `defaults settings when the row is absent`() = databaseTest {
        val settingsDao: SettingsDao = database.getSettingsDao()
        val actualSettings: Settings = settingsDao.observe().first()
        assertEquals(Settings(), actualSettings)
    }

    @Test
    fun `stores and emits the settings row`() = databaseTest {
        val expectedSettings: Settings = Settings(iconPack = TEST_ICON_PACK_PACKAGE)
        val settingsDao: SettingsDao = database.getSettingsDao()

        settingsDao.upsert(expectedSettings)

        val actualSettings: Settings = settingsDao.observe().first()
        assertEquals(expectedSettings, actualSettings)
    }

    @Test
    fun `updates one setting without clearing the others`() = databaseTest {
        val settingsDao: SettingsDao = database.getSettingsDao()
        val inputSettings: Settings = Settings(iconPack = TEST_ICON_PACK_PACKAGE)
        val expectedSettings: Settings = inputSettings.copy(hidePrivateSpaceWhenLocked = true)
        settingsDao.upsert(inputSettings)

        settingsDao.update { settings -> settings.copy(hidePrivateSpaceWhenLocked = true) }

        val actualSettings: Settings = settingsDao.observe().first()
        assertEquals(expectedSettings, actualSettings)
    }
}
