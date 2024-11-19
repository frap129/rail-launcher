package core.data.icons.model

import androidx.room.Entity
import androidx.room.PrimaryKey

const val SYSTEM_ICON_PACK_NAME: String = "System"

sealed class IconPack(open val name: String, open val icon: Icon) {
    data object SystemIconPack : IconPack(SYSTEM_ICON_PACK_NAME, Icon.ApplicationIcon("com.android.egg"))

    @Entity(tableName = "icon_packs")
    data class CustomIconPack(override val name: String, @PrimaryKey val packageName: String, override val icon: Icon.ApplicationIcon) :
        IconPack(name, icon)
}
