package core.data.icons.model

import android.content.ComponentName
import android.os.UserHandle
import androidx.room.ColumnInfo
import androidx.room.Entity
import core.data.profiles.ProfileInfo

sealed class Icon {
    data class RailIcon(val resId: Int) : Icon()

    data class ActivityIcon(val componentName: ComponentName) : Icon()

    data class ApplicationIcon(val packageName: String) : Icon()

    data class ShortcutIcon(val packageName: String, val shortcutId: String, val profile: UserHandle) : Icon()

    /** A Rail-drawn Folder icon in one of three accent shades. */
    data class FolderIcon(val shade: Int) : Icon()

    /** [icon] carrying the system badge of a non-personal [profile]. */
    data class ProfileIcon(val icon: Icon, val profile: UserHandle) : Icon()

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

/** The system-badged form of this icon for [profile], or this icon for the personal Profile. */
fun Icon.badgedFor(profile: ProfileInfo): Icon =
    if (profile.kind.isPersonal) this else Icon.ProfileIcon(icon = this, profile = profile.user)
