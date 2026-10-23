package core.data.widgets

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** A system App Widget id reserved by an add flow that has not finished yet. */
@Entity(tableName = PendingAppWidgetRecord.TABLE_NAME)
data class PendingAppWidgetRecord(@PrimaryKey @ColumnInfo(name = COLUMN_APP_WIDGET_ID) val appWidgetId: Int) {
    companion object {
        const val TABLE_NAME: String = "pending_app_widgets"
        const val COLUMN_APP_WIDGET_ID: String = "app_widget_id"
    }
}
