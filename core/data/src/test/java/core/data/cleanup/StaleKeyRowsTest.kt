package core.data.cleanup

import core.data.favorites.FavoriteRecord
import core.data.folders.Folder
import core.data.popups.IPopupRepository
import core.data.popups.PopupItemRecord
import core.data.prefs.HeaderSettings
import core.data.prefs.HeaderTapSetting
import core.data.prefs.IPreferencesRepository
import core.data.prefs.LaunchableCustomization
import core.data.room.dao.FavoriteDao
import core.data.room.dao.HeaderSettingsDao
import core.data.room.dao.PopupDao
import core.data.room.dao.StandaloneShortcutDao
import core.data.shortcuts.IStandaloneShortcutRepository
import core.data.shortcuts.StandaloneShortcutRecord
import core.data.testing.createRailLaunchable
import core.data.widgets.IWidgetAreaRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StaleKeyRowsTest {
    @Test
    fun `maps and deletes Launchable customizations`() = runTest {
        val mockPreferencesRepository: IPreferencesRepository = mockk(relaxed = true)
        every { mockPreferencesRepository.launchableCustomizations } returns MutableStateFlow(
            listOf(LaunchableCustomization(key = "first"), LaunchableCustomization(key = "second"))
        )
        val row = LaunchableCustomizationCleanupRow(preferencesRepository = mockPreferencesRepository)

        assertEquals(setOf("first", "second"), row.keys.first())
        row.delete(setOf("second"))

        coVerify(exactly = 1) { mockPreferencesRepository.removeLaunchableCustomizations(keys = listOf("second")) }
    }

    @Test
    fun `maps and deletes Favorites`() = runTest {
        val mockFavoriteDao: FavoriteDao = mockk(relaxed = true)
        every { mockFavoriteDao.observeAll() } returns MutableStateFlow(
            listOf(FavoriteRecord(key = "first", position = 0), FavoriteRecord(key = "second", position = 1))
        )
        val row = FavoriteCleanupRow(favoriteDao = mockFavoriteDao)

        assertEquals(setOf("first", "second"), row.keys.first())
        row.delete(setOf("second"))

        coVerify(exactly = 1) { mockFavoriteDao.remove(keys = listOf("second")) }
    }

    @Test
    fun `maps and deletes Pop-up owners`() = runTest {
        val mockPopupDao: PopupDao = mockk(relaxed = true)
        val mockWidgetAreaRepository: IWidgetAreaRepository = mockk(relaxed = true)
        val mockPopupRepository: IPopupRepository = mockk(relaxed = true)
        every { mockPopupDao.observeAllOwnerKeys() } returns MutableStateFlow(listOf("hidden"))
        every { mockPopupDao.observeAllItems() } returns MutableStateFlow(
            listOf(PopupItemRecord(ownerKey = "owner", itemKey = "item", position = 0))
        )
        every { mockWidgetAreaRepository.popupOwnerKeys } returns MutableStateFlow(listOf("widget"))
        coEvery { mockPopupRepository.deleteOwner(any()) } returns emptyList()
        val row = PopupOwnerCleanupRow(
            popupDao = mockPopupDao,
            widgetAreaRepository = mockWidgetAreaRepository,
            popupRepository = mockPopupRepository
        )

        assertEquals(setOf("hidden", "owner", "widget"), row.keys.first())
        row.delete(setOf("owner", "widget"))

        coVerify(exactly = 1) { mockPopupRepository.deleteOwner(ownerKey = "owner") }
        coVerify(exactly = 1) { mockPopupRepository.deleteOwner(ownerKey = "widget") }
    }

    @Test
    fun `maps and deletes Pop-up items`() = runTest {
        val mockPopupDao: PopupDao = mockk(relaxed = true)
        val mockPopupRepository: IPopupRepository = mockk(relaxed = true)
        every { mockPopupDao.observeAllItems() } returns MutableStateFlow(
            listOf(
                PopupItemRecord(ownerKey = "owner", itemKey = "first", position = 0),
                PopupItemRecord(ownerKey = "owner", itemKey = "second", position = 1)
            )
        )
        val row = PopupItemCleanupRow(popupDao = mockPopupDao, popupRepository = mockPopupRepository)

        assertEquals(setOf("first", "second"), row.keys.first())
        row.delete(setOf("second"))

        coVerify(exactly = 1) { mockPopupRepository.deleteItemsByKey(itemKeys = listOf("second")) }
    }

    @Test
    fun `maps and clears Header tap actions`() = runTest {
        val mockHeaderSettingsDao = FakeHeaderSettingsDao(
            HeaderSettings(
                timeTapTarget = HeaderTapSetting.Launchable(key = "gone"),
                dateTapTarget = HeaderTapSetting.Launchable(key = "kept")
            )
        )
        val row = HeaderTapTargetCleanupRow(headerSettingsDao = mockHeaderSettingsDao)

        assertEquals(setOf("gone", "kept"), row.keys.first())
        row.delete(setOf("gone"))

        assertNull(mockHeaderSettingsDao.settings.value.timeTapTarget)
        assertEquals(HeaderTapSetting.Launchable(key = "kept"), mockHeaderSettingsDao.settings.value.dateTapTarget)
    }

    @Test
    fun `treats a Header tap action to a Folder as stale`() {
        val row = HeaderTapTargetCleanupRow(headerSettingsDao = FakeHeaderSettingsDao(HeaderSettings()))
        val inputFolder = Folder(folderId = 1L)

        assertTrue(row.isStale(StoredKeyState.Present(launchable = inputFolder)))
        assertFalse(row.isStale(StoredKeyState.Present(launchable = createRailLaunchable(name = "App"))))
        assertTrue(row.isStale(StoredKeyState.Stale))
    }

    @Test
    fun `maps and deletes Standalone shortcuts`() = runTest {
        val mockStandaloneShortcutDao: StandaloneShortcutDao = mockk(relaxed = true)
        val mockStandaloneShortcutRepository: IStandaloneShortcutRepository = mockk(relaxed = true)
        every { mockStandaloneShortcutDao.observeAll() } returns MutableStateFlow(
            listOf(StandaloneShortcutRecord(key = "first", addedAt = 1L), StandaloneShortcutRecord(key = "second", addedAt = 2L))
        )
        val row = StandaloneShortcutCleanupRow(
            standaloneShortcutDao = mockStandaloneShortcutDao,
            standaloneShortcutRepository = mockStandaloneShortcutRepository
        )

        assertEquals(setOf("first", "second"), row.keys.first())
        row.delete(setOf("second"))

        coVerify(exactly = 1) { mockStandaloneShortcutRepository.delete(keys = listOf("second")) }
    }
}

private class FakeHeaderSettingsDao(initialSettings: HeaderSettings) : HeaderSettingsDao() {
    val settings: MutableStateFlow<HeaderSettings> = MutableStateFlow(initialSettings)

    override fun observeRow(): Flow<HeaderSettings?> = settings

    override suspend fun getRow(): HeaderSettings = settings.value

    override suspend fun upsert(row: HeaderSettings) {
        settings.value = row
    }
}
