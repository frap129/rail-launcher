package core.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import core.data.folders.FolderRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao : TransactionalDao {
    @Query("SELECT * FROM folders ORDER BY id")
    fun observeAll(): Flow<List<FolderRecord>>

    @Insert
    suspend fun insert(record: FolderRecord): Long

    @Query("UPDATE folders SET show_in_app_list = :showInAppList WHERE id = :id")
    suspend fun setShowInAppList(id: Long, showInAppList: Boolean)

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun delete(id: Long)
}
