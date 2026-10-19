package core.data.prefs

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "launchable_customizations")
data class LaunchableCustomization(@PrimaryKey val key: String, val name: String? = null, val icon: String? = null)
