package core.data.icons.model

import android.content.ComponentName
import android.os.UserHandle
import androidx.room.ColumnInfo
import androidx.room.Entity

sealed class Icon {
    data class RailIcon(val resId: Int) : Icon()

    data class ActivityIcon(val componentName: ComponentName) : Icon()

    data class ApplicationIcon(val packageName: String) : Icon()

    data class ShortcutIcon(val packageName: String, val shortcutId: String, val profile: UserHandle) : Icon()

    @Entity(
        tableName = "icons",
        primaryKeys = [PackIcon.COLUMN_COMPONENT_NAME, PackIcon.COLUMN_PACK_PACKAGE_NAME]
    )
    data class PackIcon(
        @ColumnInfo(name = PackIcon.COLUMN_COMPONENT_NAME) val componentName: ComponentName,
        @ColumnInfo(name = PackIcon.COLUMN_PACK_PACKAGE_NAME) val packPackageName: String,
        @ColumnInfo(name = PackIcon.COLUMN_RES_ID) val resId: Int
    ) : Icon() {
        companion object {
            const val COLUMN_COMPONENT_NAME: String = "component_name"
            const val COLUMN_PACK_PACKAGE_NAME: String = "pack_package_name"
            const val COLUMN_RES_ID: String = "res_id"
        }
    }
}
