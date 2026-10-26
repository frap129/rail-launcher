package core.data.room.dao

import core.data.popups.PopupItemRecord
import core.data.room.AppDatabase
import core.data.testing.TEST_SDK
import core.data.testing.createInMemoryDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val OWNER_KEY: String = "com.example.owner"
private const val OTHER_OWNER_KEY: String = "com.example.other_owner"
private const val CONCURRENT_WRITES: Int = 16

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [TEST_SDK])
class PopupDaoTest {
    @Test
    fun `appendItems gives concurrent appends contiguous positions`() = runTest {
        val mockDatabase: AppDatabase = createInMemoryDatabase()
        try {
            val dao: PopupDao = mockDatabase.getPopupDao()
            val inputKeys: List<String> = (0 until CONCURRENT_WRITES).map { index -> "com.example.app_$index" }

            inputKeys
                .map { inputKey -> async(Dispatchers.Default) { dao.appendItems(ownerKey = OWNER_KEY, itemKeys = listOf(inputKey)) } }
                .awaitAll()

            val actualItems: List<PopupItemRecord> = dao.getAllItems()
            assertEquals(CONCURRENT_WRITES, actualItems.size)
            assertEquals((0 until CONCURRENT_WRITES).toList(), actualItems.map { item -> item.position }.sorted())
            assertEquals(inputKeys.toSet(), actualItems.map { item -> item.itemKey }.toSet())
        } finally {
            mockDatabase.close()
        }
    }

    @Test
    fun `appendItems adds to the owner's existing positions`() = runTest {
        val mockDatabase: AppDatabase = createInMemoryDatabase()
        try {
            val dao: PopupDao = mockDatabase.getPopupDao()
            dao.insertItems(listOf(PopupItemRecord(ownerKey = OTHER_OWNER_KEY, itemKey = "com.example.other", position = 0)))

            dao.appendItems(ownerKey = OWNER_KEY, itemKeys = listOf("com.example.first", "com.example.second"))

            assertEquals(
                listOf(
                    PopupItemRecord(ownerKey = OWNER_KEY, itemKey = "com.example.first", position = 0),
                    PopupItemRecord(ownerKey = OWNER_KEY, itemKey = "com.example.second", position = 1)
                ),
                dao.getItems(OWNER_KEY)
            )
        } finally {
            mockDatabase.close()
        }
    }

    @Test
    fun `setShortcutHidden keeps concurrent toggles consistent`() = runTest {
        val mockDatabase: AppDatabase = createInMemoryDatabase()
        try {
            val dao: PopupDao = mockDatabase.getPopupDao()
            val inputShortcutIds: List<String> = (0 until CONCURRENT_WRITES).map { index -> "shortcut_$index" }

            inputShortcutIds
                .map { inputShortcutId -> async(Dispatchers.Default) { dao.setShortcutHidden(OWNER_KEY, inputShortcutId, hidden = true) } }
                .awaitAll()
            inputShortcutIds
                .map { inputShortcutId -> async(Dispatchers.Default) { dao.setShortcutHidden(OWNER_KEY, inputShortcutId, hidden = false) } }
                .awaitAll()

            assertTrue(dao.observeHiddenShortcuts(OWNER_KEY).first().isEmpty())
        } finally {
            mockDatabase.close()
        }
    }
}
