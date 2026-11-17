package core.data.room

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import core.data.apps.LastKnownAppRecord
import core.data.room.dao.LastKnownAppDao

@Database(entities = [LastKnownAppRecord::class], version = 1)
@TypeConverters(Converters::class)
abstract class LastKnownAppDatabase : RoomDatabase() {
    abstract fun getLastKnownAppDao(): LastKnownAppDao

    companion object {
        const val DATABASE_NAME: String = "last-known-apps"
    }
}
