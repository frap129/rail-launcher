package core.data.widgets

import core.data.prefs.HeaderSettings
import core.data.testing.FakeHeaderSettingsDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class IWidgetSettingsRepositoryImplTest {
    @Test
    fun `returns defaults when the settings row is absent`() = runTest {
        val repository: IWidgetSettingsRepository = IWidgetSettingsRepositoryImpl(FakeHeaderSettingsDao())

        assertEquals(HeaderSettings(), repository.headerSettings.first())
    }

    @Test
    fun `persists a transformed Header setting`() = runTest {
        val repository: IWidgetSettingsRepository = IWidgetSettingsRepositoryImpl(FakeHeaderSettingsDao())

        repository.updateHeaderSettings { settings -> settings.copy(clockVisible = false) }

        assertEquals(HeaderSettings(clockVisible = false), repository.headerSettings.first())
    }
}
