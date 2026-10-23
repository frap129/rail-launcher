package core.data.room.dao

import core.data.prefs.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsDaoTest {
    @Test
    fun `defaults settings when the row is absent`() = runTest {
        val mockSettingsDao: SettingsDao = FakeSettingsDao(settingsRow = null)
        val actualSettings: Settings = mockSettingsDao.observe().first()
        assertEquals(Settings(), actualSettings)
    }

    @Test
    fun `emits the stored settings row when present`() = runTest {
        val expectedSettings: Settings = Settings(iconPack = "com.example.iconpack")
        val mockSettingsDao: SettingsDao = FakeSettingsDao(settingsRow = expectedSettings)
        val actualSettings: Settings = mockSettingsDao.observe().first()
        assertEquals(expectedSettings, actualSettings)
    }

    private class FakeSettingsDao(private val settingsRow: Settings?) : SettingsDao() {
        override fun observeRow(): Flow<Settings?> = flowOf(settingsRow)

        override suspend fun upsert(settings: Settings) = Unit
    }
}
