package core.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import core.data.widgets.PendingAppWidgetRecord

@Dao
abstract class PendingAppWidgetDao {
    @Query("SELECT " + PendingAppWidgetRecord.COLUMN_APP_WIDGET_ID + " FROM " + PendingAppWidgetRecord.TABLE_NAME)
    abstract suspend fun getIds(): List<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAll(records: List<PendingAppWidgetRecord>)

    @Query("DELETE FROM " + PendingAppWidgetRecord.TABLE_NAME)
    abstract suspend fun deleteAll()

    @Transaction
    open suspend fun replace(ids: List<Int>) {
        deleteAll()
        insertAll(ids.map(::PendingAppWidgetRecord))
    }
}
