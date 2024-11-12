package core.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import core.data.launcher.LauncherItem
import core.util.launchInBackground
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PreferencesRepository(val context: Context) {
    val Context.itemNames: DataStore<Preferences> by preferencesDataStore(name = "names")
    val itemNames = context.itemNames.data
    val Context.itemIcons: DataStore<Preferences> by preferencesDataStore(name = "icons")
    val Context.appSettings: DataStore<Preferences> by preferencesDataStore(name = "settings")

    fun getItemName(item: LauncherItem): Flow<String> = context.itemNames.data.map { names ->
        names[stringPreferencesKey(item.key)] ?: item.defaultName
    }

    fun setItemName(item: LauncherItem, name: String) {
        launchInBackground {
            context.itemNames.edit { names ->
                names[stringPreferencesKey(item.key)] = name
            }
        }
    }
}
