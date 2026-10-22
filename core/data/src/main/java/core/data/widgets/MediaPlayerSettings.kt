package core.data.widgets

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import core.data.room.ExcludedPackagesConverters

/** Where the Media Player sits relative to the Widget Area. */
enum class MediaPlayerPlacement {
    BELOW_WIDGET_AREA,
    TOP_OF_WIDGET_AREA
}

/** Persisted preferences for the built-in Media Player. */
@Entity(tableName = "media_player_settings")
@TypeConverters(ExcludedPackagesConverters::class)
data class MediaPlayerSettings(
    @PrimaryKey val id: Int = 0,
    @ColumnInfo(name = "enabled", defaultValue = "0") val enabled: Boolean = false,
    @ColumnInfo(name = "show_when_paused", defaultValue = "1") val showWhenPaused: Boolean = true,
    @ColumnInfo(name = "show_artwork", defaultValue = "1") val showArtwork: Boolean = true,
    @ColumnInfo(name = "placement", defaultValue = "BELOW_WIDGET_AREA") val placement: MediaPlayerPlacement =
        MediaPlayerPlacement.BELOW_WIDGET_AREA,
    @ColumnInfo(name = "excluded_packages", defaultValue = "") val excludedPackages: Set<String> = emptySet()
) {
    companion object {
        val Default: MediaPlayerSettings = MediaPlayerSettings()
    }
}
