package core.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import core.data.apps.LastKnownAppRecord

@Dao
abstract class LastKnownAppDao {
    @Query("SELECT * FROM " + LastKnownAppRecord.TABLE_NAME)
    abstract suspend fun getAll(): List<LastKnownAppRecord>

    @Insert
    abstract suspend fun insertAll(records: List<LastKnownAppRecord>)

    @Query("DELETE FROM " + LastKnownAppRecord.TABLE_NAME)
    abstract suspend fun deleteAll()

    @Transaction
    open suspend fun replace(records: List<LastKnownAppRecord>) {
        deleteAll()
        insertAll(records = records)
    }
}
