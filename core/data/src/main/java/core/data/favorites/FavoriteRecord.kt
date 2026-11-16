package core.data.favorites

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A stored Favorite: the key of a Launchable and its place in the user's order. */
@Entity(tableName = FavoriteRecord.TABLE_NAME)
data class FavoriteRecord(@PrimaryKey val key: String, val position: Int) {
    companion object {
        const val TABLE_NAME: String = "favorites"
    }
}
