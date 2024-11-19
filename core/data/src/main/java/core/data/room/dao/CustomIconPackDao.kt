package core.data.room.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import core.data.icons.model.IconPack

@Dao
interface CustomIconPackDao {
    @Query("SELECT * FROM icon_packs")
    suspend fun getAll(): List<IconPack.CustomIconPack>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(iconPacks: List<IconPack.CustomIconPack>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(iconPack: IconPack.CustomIconPack)

    @Delete
    suspend fun deleteAll(iconPacks: List<IconPack.CustomIconPack>)

    @Delete
    suspend fun delete(iconPack: IconPack.CustomIconPack)
}
