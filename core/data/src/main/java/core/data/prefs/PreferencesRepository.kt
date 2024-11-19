package core.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import core.data.icons.model.IconPack
import core.data.launchables.model.Launchable
import core.util.launchInBackground
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PreferencesRepository(val context: Context) {
    private val Context.itemNames: DataStore<Preferences> by preferencesDataStore(name = "names")
    private val Context.itemIcons: DataStore<Preferences> by preferencesDataStore(name = "icons")
    private val Context.appSettings: DataStore<Preferences> by preferencesDataStore(name = "settings")
    val itemNames = context.itemNames.data
    val itemIcons = context.itemIcons.data

    enum class AppPreferences(val key: Preferences.Key<String>) {
        ICON_PACK(stringPreferencesKey("iconPack"))
    }

    fun getItemName(item: Launchable): Flow<String> = context.itemNames.data.map { names ->
        names[stringPreferencesKey(item.key)] ?: item.defaultName
    }

    fun setItemName(item: Launchable, name: String) {
        launchInBackground {
            context.itemNames.edit { names ->
                names[stringPreferencesKey(item.key)] = name
            }
        }
    }

    fun getIconPackName(): Flow<String?> = context.itemIcons.data.map { settings ->
        settings[AppPreferences.ICON_PACK.key]
    }

    fun setIconPack(iconPack: IconPack) {
        launchInBackground {
            context.itemIcons.edit { icons ->
                icons[AppPreferences.ICON_PACK.key] = iconPack.name
            }
        }
    }
}
