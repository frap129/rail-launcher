package core.data.room.dao

import core.data.popups.PopupItemRecord
import core.data.testing.InMemoryDatabaseTest
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val OWNER_KEY: String = "com.example.owner"
private const val OTHER_OWNER_KEY: String = "com.example.other_owner"
private const val CONCURRENT_WRITES: Int = 16

class PopupDaoTest : InMemoryDatabaseTest() {
    @Test
    fun `appendItems gives concurrent appends contiguous positions`() = databaseTest {
        val dao: PopupDao = database.getPopupDao()
        val inputKeys: List<String> = (0 until CONCURRENT_WRITES).map { index -> "com.example.app_$index" }

        inputKeys
            .map { inputKey -> async { dao.appendItems(ownerKey = OWNER_KEY, itemKeys = listOf(inputKey)) } }
            .awaitAll()

        val actualItems: List<PopupItemRecord> = dao.getAllItems()
        assertEquals(CONCURRENT_WRITES, actualItems.size)
        assertEquals((0 until CONCURRENT_WRITES).toList(), actualItems.map { item -> item.position }.sorted())
        assertEquals(inputKeys.toSet(), actualItems.map { item -> item.itemKey }.toSet())
    }

    @Test
    fun `appendItems adds to the owner's existing positions`() = databaseTest {
        val dao: PopupDao = database.getPopupDao()
        dao.insertItems(listOf(PopupItemRecord(ownerKey = OTHER_OWNER_KEY, itemKey = "com.example.other", position = 0)))

        dao.appendItems(ownerKey = OWNER_KEY, itemKeys = listOf("com.example.first", "com.example.second"))

        assertEquals(
            listOf(
                PopupItemRecord(ownerKey = OWNER_KEY, itemKey = "com.example.first", position = 0),
                PopupItemRecord(ownerKey = OWNER_KEY, itemKey = "com.example.second", position = 1)
            ),
            dao.getItems(OWNER_KEY)
        )
    }

    @Test
    fun `setShortcutHidden keeps concurrent toggles consistent`() = databaseTest {
        val dao: PopupDao = database.getPopupDao()
        val inputShortcutIds: List<String> = (0 until CONCURRENT_WRITES).map { index -> "shortcut_$index" }

        inputShortcutIds
            .map { inputShortcutId -> async { dao.setShortcutHidden(OWNER_KEY, inputShortcutId, hidden = true) } }
            .awaitAll()
        inputShortcutIds
            .map { inputShortcutId -> async { dao.setShortcutHidden(OWNER_KEY, inputShortcutId, hidden = false) } }
            .awaitAll()

        assertTrue(dao.observeHiddenShortcuts(OWNER_KEY).first().isEmpty())
    }
}
