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
        val settingsDao: SettingsDao = FakeSettingsDao(settingsRow = null)
        val actual: Settings = settingsDao.observe().first()
        assertEquals(Settings(), actual)
    }

    @Test
    fun `emits the stored settings row when present`() = runTest {
        val expected: Settings = Settings(iconPack = "com.example.iconpack")
        val settingsDao: SettingsDao = FakeSettingsDao(settingsRow = expected)
        val actual: Settings = settingsDao.observe().first()
        assertEquals(expected, actual)
    }

    private class FakeSettingsDao(private val settingsRow: Settings?) : SettingsDao() {
        override fun observeRow(): Flow<Settings?> = flowOf(settingsRow)

        override suspend fun upsert(settings: Settings) = Unit
    }
}
