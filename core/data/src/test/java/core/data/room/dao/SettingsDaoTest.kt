package core.data.room.dao

import core.data.prefs.Settings
import core.data.testing.InMemoryDatabaseTest
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
        val expectedSettings: Settings = Settings(iconPack = "com.example.iconpack")
        val settingsDao: SettingsDao = database.getSettingsDao()

        settingsDao.upsert(expectedSettings)

        val actualSettings: Settings = settingsDao.observe().first()
        assertEquals(expectedSettings, actualSettings)
    }
}
