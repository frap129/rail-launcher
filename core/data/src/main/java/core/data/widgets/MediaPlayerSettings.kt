package core.data.widgets

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** Persisted preferences for the built-in Media Player. */
@Entity(tableName = "media_player_settings")
data class MediaPlayerSettings(
    @PrimaryKey val id: Int = 0,
    @ColumnInfo(name = "enabled", defaultValue = "0") val enabled: Boolean = false
) {
    companion object {
        val Default: MediaPlayerSettings = MediaPlayerSettings()
    }
}
