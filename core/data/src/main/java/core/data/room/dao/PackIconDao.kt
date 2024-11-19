package core.data.room.dao

import android.content.ComponentName
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import core.data.icons.model.Icon

@Dao
interface PackIconDao {
    @Query("SELECT * FROM icons")
    suspend fun getAll(): List<Icon.PackIcon>

    @Query("SELECT * FROM icons WHERE packPackageName = :packPackageName")
    suspend fun getAllFromPack(packPackageName: String): List<Icon.PackIcon>

    @Query("SELECT * FROM icons WHERE packPackageName = :packPackageName AND componentName = :componentName")
    suspend fun getIcon(componentName: ComponentName, packPackageName: String): Icon.PackIcon?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(icons: List<Icon.PackIcon>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(icon: Icon.PackIcon)

    @Delete
    suspend fun deleteAll(icons: List<Icon.PackIcon>)

    @Delete
    suspend fun delete(icon: Icon.PackIcon)
}
