package core.data.icons.model

import android.content.ComponentName
import android.os.UserHandle
import androidx.room.ColumnInfo
import androidx.room.Entity
import core.data.profiles.ProfileInfo

/**
 * The update time of an App icon whose caller reads none, as App Widget host icons do: an icon
 * that carries it never takes part in the Home icon cache.
 */
const val UNKNOWN_APP_UPDATE_TIME: Long = 0L

sealed class Icon {
    data class RailIcon(val resId: Int) : Icon()

    /** The launcher activity icon of [componentName] from the App package updated at [lastUpdateTime], or [UNKNOWN_APP_UPDATE_TIME]. */
    data class ActivityIcon(val componentName: ComponentName, val lastUpdateTime: Long) : Icon()

    data class ApplicationIcon(val packageName: String) : Icon()

    data class ShortcutIcon(val packageName: String, val shortcutId: String, val profile: UserHandle) : Icon()

    /** A Rail-drawn Folder icon in one of three accent shades. */
    data class FolderIcon(val shade: Int) : Icon()

    /** A drawable from an icon pack, stored by name so a pack update can keep it. */
    data class PackDrawableIcon(val packPackageName: String, val drawableName: String) : Icon()

    /** A gallery image file in Rail's private icons directory, drawn clipped to the system icon mask. */
    data class ImageIcon(val fileName: String) : Icon()

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

/** The Activity icon of [componentName], badged for [profile]: what an App without a pack or Custom icon shows. */
fun activityIconFor(componentName: ComponentName, profile: ProfileInfo, lastUpdateTime: Long): Icon =
    Icon.ActivityIcon(componentName = componentName, lastUpdateTime = lastUpdateTime).badgedFor(profile = profile)
