package core.data.room

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import core.data.icons.model.Icon
import core.data.icons.model.IconPack
import core.data.room.dao.CustomIconPackDao
import core.data.room.dao.PackIconDao

@Database(
    entities = [
        IconPack.CustomIconPack::class,
        Icon.PackIcon::class
    ],
    version = 1
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun getCachedIconPackDao(): CustomIconPackDao
    abstract fun getPackIconDao(): PackIconDao
}
