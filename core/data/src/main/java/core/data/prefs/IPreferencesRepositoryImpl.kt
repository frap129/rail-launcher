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

class IPreferencesRepositoryImpl(val context: Context) : IPreferencesRepository {
    private val Context.itemNames: DataStore<Preferences> by preferencesDataStore(name = "names")
    private val Context.itemIcons: DataStore<Preferences> by preferencesDataStore(name = "icons")
    private val Context.appSettings: DataStore<Preferences> by preferencesDataStore(name = "settings")
    override val itemNames = context.itemNames.data
    override val itemIcons = context.itemIcons.data

    enum class AppPreferences(val key: Preferences.Key<String>) {
        ICON_PACK(stringPreferencesKey("iconPack"))
    }

    override fun getItemName(item: Launchable): Flow<String> = context.itemNames.data.map { names ->
        names[stringPreferencesKey(item.key)] ?: item.defaultName
    }

    override fun setItemName(item: Launchable, name: String) {
        launchInBackground {
            context.itemNames.edit { names ->
                names[stringPreferencesKey(item.key)] = name
            }
        }
    }

    override fun getIconPackName(): Flow<String?> = context.appSettings.data.map { settings ->
        settings[AppPreferences.ICON_PACK.key]
    }

    override fun setIconPack(iconPack: IconPack) {
        launchInBackground {
            context.appSettings.edit { settings ->
                if (iconPack is IconPack.CustomIconPack) {
                    settings[AppPreferences.ICON_PACK.key] = iconPack.packageName
                } else {
                    settings[AppPreferences.ICON_PACK.key] = ""
                }
            }
        }
    }
}
