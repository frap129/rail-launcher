package core.data.launchables

import android.content.ComponentName
import core.data.apps.model.App
import core.data.favorites.FavoriteRecord
import core.data.folders.Folder
import core.data.popups.PopupItemRecord
import core.data.prefs.HeaderSettings
import core.data.prefs.HeaderTapSetting
import core.data.prefs.LaunchableCustomization
import core.data.room.dao.HeaderSettingsDao
import core.data.testing.FIRST_POSITION
import core.data.testing.InMemoryDatabaseTest
import core.data.testing.PERSONAL_USER_ID
import core.data.testing.SECOND_POSITION
import core.data.testing.createApp
import core.data.testing.createFailOnceMock
import core.data.testing.createLegacyAppKeyUpgrader
import core.data.testing.createProfileInfo
import core.data.widgets.WidgetKind
import core.data.widgets.WidgetPlaceRecord
import core.data.widgets.WidgetRecord
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val PACKAGE_NAME: String = "com.example.dpc"
private const val GONE_PACKAGE_NAME: String = "com.example.gone"
private const val REPRESENTATIVE_ACTIVITY: String = "$PACKAGE_NAME.PolicyManagementActivity"
private const val OTHER_ACTIVITY: String = "$PACKAGE_NAME.SetupManagementLaunchActivity"
private const val SHORTCUT_ID: String = "shortcut-id"
private const val CUSTOM_NAME: String = "DPC"
private const val CUSTOM_NAME_KEY: String = "custom-name"

class ILegacyAppKeyUpgraderImplTest : InMemoryDatabaseTest() {
    private val inputProfile = createProfileInfo(userId = PERSONAL_USER_ID)
    private val inputRepresentative: App = createApp(name = "DPC", packageName = PACKAGE_NAME, profileInfo = inputProfile).copy(
        componentName = ComponentName(PACKAGE_NAME, REPRESENTATIVE_ACTIVITY),
        isPackageRepresentative = true
    )
    private val inputOtherActivity: App = inputRepresentative.copy(
        componentName = ComponentName(PACKAGE_NAME, OTHER_ACTIVITY),
        isPackageRepresentative = false
    )
    private val inputFolder: Folder = Folder(folderId = 1L)
    private val inputLegacyKey: String = "${PACKAGE_NAME}_${inputProfile.user}"
    private val inputGoneLegacyKey: String = "${GONE_PACKAGE_NAME}_${inputProfile.user}"

    @Test
    fun `rewrites Legacy App keys in every key-bearing table`() = databaseTest {
        seedFavorite(key = inputGoneLegacyKey, position = 0)
        seedFavorite(key = inputLegacyKey, position = FIRST_POSITION)
        seedFavorite(key = inputRepresentative.key, position = SECOND_POSITION)
        seedKeyBearingRows()
        val upgrader = createLegacyAppKeyUpgrader(database)

        upgrader.upgrade(apps = listOf(inputRepresentative, inputOtherActivity))

        assertEquals(
            listOf(
                FavoriteRecord(key = inputGoneLegacyKey, position = 0),
                FavoriteRecord(key = inputRepresentative.key, position = SECOND_POSITION)
            ),
            favoriteRecords().sortedBy { record -> record.position }
        )
        assertKeyBearingRowsStoredUnder(key = inputRepresentative.key)
    }

    @Test
    fun `renumbers Pop-up items after merging a Legacy owner`() = databaseTest {
        database.getPopupDao().insertItems(
            listOf(
                PopupItemRecord(ownerKey = inputRepresentative.key, itemKey = "first", position = 0),
                PopupItemRecord(ownerKey = inputLegacyKey, itemKey = "second", position = 0)
            )
        )
        val upgrader = createLegacyAppKeyUpgrader(database)

        upgrader.upgrade(apps = listOf(inputRepresentative, inputOtherActivity))

        val actualItems: List<PopupItemRecord> = database.getPopupDao().getItems(inputRepresentative.key)
        assertEquals(listOf(0, 1), actualItems.map { item -> item.position }.sorted())
        assertEquals(setOf("first", "second"), actualItems.map { item -> item.itemKey }.toSet())
        assertTrue(database.getPopupDao().getItems(inputLegacyKey).isEmpty())
    }

    @Test
    fun `merges a Legacy Pop-up Place into an existing current Place`() = databaseTest {
        seedWidgetArea(
            places = listOf(
                WidgetPlaceRecord(id = 1L, position = 0, ownerKey = inputRepresentative.key),
                WidgetPlaceRecord(id = 2L, position = 0, ownerKey = inputLegacyKey)
            ),
            widgets = listOf(
                appWidget(id = 10L, placeId = 1L, appWidgetId = 100),
                appWidget(id = 20L, placeId = 2L, appWidgetId = 200)
            )
        )
        val upgrader = createLegacyAppKeyUpgrader(database)

        upgrader.upgrade(apps = listOf(inputRepresentative, inputOtherActivity))

        val actualPlace: WidgetPlaceRecord = requireNotNull(database.getWidgetAreaDao().getPlaceForOwner(inputRepresentative.key))
        assertEquals(listOf(10L, 20L), database.getWidgetAreaDao().getWidgetsForPlace(actualPlace.id).map { widget -> widget.id })
        assertNull(database.getWidgetAreaDao().getPlaceForOwner(inputLegacyKey))
    }

    @Test
    fun `keeps a canonical customization over a Legacy App key`() = databaseTest {
        database.getLaunchableCustomizationDao().setName(key = inputLegacyKey, name = CUSTOM_NAME)
        database.getLaunchableCustomizationDao().setName(key = inputRepresentative.key, name = CUSTOM_NAME_KEY)
        val upgrader = createLegacyAppKeyUpgrader(database)

        upgrader.upgrade(apps = listOf(inputRepresentative, inputOtherActivity))

        assertEquals(
            listOf(LaunchableCustomization(key = inputRepresentative.key, name = CUSTOM_NAME_KEY)),
            customizations()
        )
    }

    @Test
    fun `is idempotent`() = databaseTest {
        seedFavorite(key = inputLegacyKey, position = FIRST_POSITION)
        val upgrader = createLegacyAppKeyUpgrader(database)
        upgrader.upgrade(apps = listOf(inputRepresentative, inputOtherActivity))
        val actualFirst: List<FavoriteRecord> = favoriteRecords()

        upgrader.upgrade(apps = listOf(inputRepresentative, inputOtherActivity))

        assertEquals(actualFirst, favoriteRecords())
        assertTrue(actualFirst.any { record -> record.key == inputRepresentative.key })
    }

    @Test
    fun `rolls back a key when a mid-upgrade write fails and upgrades it on the next run`() = databaseTest {
        seedFavorite(key = inputLegacyKey, position = FIRST_POSITION)
        seedKeyBearingRows()
        val mockHeaderSettingsDao: HeaderSettingsDao = database.getHeaderSettingsDao().createFailOnceMock(
            failure = IllegalStateException("Header write failed"),
            stub = { dao -> dao.upsert(any()) },
            delegate = { dao -> dao.upsert(firstArg()) }
        )
        val upgrader = createLegacyAppKeyUpgrader(database = database, headerSettingsDao = mockHeaderSettingsDao)

        val actualFailure: Throwable? = runCatching {
            upgrader.upgrade(apps = listOf(inputRepresentative, inputOtherActivity))
        }.exceptionOrNull()

        assertTrue(actualFailure is IllegalStateException)
        assertEquals(listOf(FavoriteRecord(key = inputLegacyKey, position = FIRST_POSITION)), favoriteRecords())
        assertKeyBearingRowsStoredUnder(key = inputLegacyKey)

        upgrader.upgrade(apps = listOf(inputRepresentative, inputOtherActivity))

        assertEquals(
            listOf(FavoriteRecord(key = inputRepresentative.key, position = FIRST_POSITION)),
            favoriteRecords()
        )
        assertKeyBearingRowsStoredUnder(key = inputRepresentative.key)
    }

    private suspend fun seedKeyBearingRows() {
        database.getLaunchableCustomizationDao().setName(key = inputLegacyKey, name = CUSTOM_NAME)
        database.getHeaderSettingsDao().upsert(
            HeaderSettings(
                timeTapTarget = HeaderTapSetting.Launchable(key = inputLegacyKey),
                dateTapTarget = HeaderTapSetting.Nothing
            )
        )
        database.getPopupDao().insertItems(
            listOf(
                PopupItemRecord(ownerKey = inputLegacyKey, itemKey = inputOtherActivity.key, position = 0),
                PopupItemRecord(ownerKey = inputFolder.key, itemKey = inputLegacyKey, position = 0),
                PopupItemRecord(ownerKey = inputLegacyKey, itemKey = inputLegacyKey, position = 1)
            )
        )
        database.getPopupDao().insertHiddenShortcut(ownerKey = inputLegacyKey, shortcutId = SHORTCUT_ID)
        database.getWidgetAreaDao().insertPlace(WidgetPlaceRecord(position = 0, ownerKey = inputLegacyKey))
    }

    private suspend fun assertKeyBearingRowsStoredUnder(key: String) {
        assertEquals(listOf(LaunchableCustomization(key = key, name = CUSTOM_NAME)), customizations())
        val actualHeaderSettings: HeaderSettings = database.getHeaderSettingsDao().getRow()!!
        assertEquals(HeaderTapSetting.Launchable(key = key), actualHeaderSettings.timeTapTarget)
        assertEquals(HeaderTapSetting.Nothing, actualHeaderSettings.dateTapTarget)
        assertEquals(
            listOf(
                PopupItemRecord(ownerKey = key, itemKey = inputOtherActivity.key, position = 0),
                PopupItemRecord(ownerKey = key, itemKey = key, position = 1),
                PopupItemRecord(ownerKey = inputFolder.key, itemKey = key, position = 0)
            ),
            database.getPopupDao().getAllItems().sortedWith(compareBy({ item -> item.ownerKey }, { item -> item.position }))
        )
        assertEquals(listOf(key), database.getPopupDao().observeAllOwnerKeys().first())
        assertEquals(listOf(key), database.getWidgetAreaDao().getPopupOwnerKeys())
    }

    private fun appWidget(id: Long, placeId: Long, appWidgetId: Int): WidgetRecord = WidgetRecord(
        id = id,
        placeId = placeId,
        pagePosition = 0,
        kind = WidgetKind.APP_WIDGET,
        appWidgetId = appWidgetId,
        provider = "provider",
        profileSerial = 0L
    )
}
