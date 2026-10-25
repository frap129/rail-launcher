package core.data.widgets

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "widget_places", indices = [Index("owner_key")])
data class WidgetPlaceRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val position: Int,
    @ColumnInfo(name = "height_dp") val heightDp: Int? = null,
    @ColumnInfo(name = "current_page", defaultValue = "0") val currentPage: Int = 0,
    @ColumnInfo(name = "owner_key") val ownerKey: String? = null
)

@Entity(
    tableName = "widgets",
    foreignKeys = [
        ForeignKey(
            entity = WidgetPlaceRecord::class,
            parentColumns = ["id"],
            childColumns = ["place_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("place_id")]
)
data class WidgetRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "place_id") val placeId: Long,
    @ColumnInfo(name = "page_position") val pagePosition: Int,
    val kind: WidgetKind,
    @ColumnInfo(name = "app_widget_id") val appWidgetId: Int? = null,
    val provider: String? = null,
    @ColumnInfo(name = "profile_serial") val profileSerial: Long? = null,
    @ColumnInfo(name = "extends_under_rail", defaultValue = "0") val extendsUnderRail: Boolean = false,
    @ColumnInfo(name = "default_height_dp") val defaultHeightDp: Int? = null
)

enum class WidgetKind {
    HEADER,
    APP_WIDGET,

    /** A value written by a newer schema that this build does not understand. */
    UNKNOWN
}
