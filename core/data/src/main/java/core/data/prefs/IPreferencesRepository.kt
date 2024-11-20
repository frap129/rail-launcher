package core.data.prefs

import androidx.datastore.preferences.core.Preferences
import core.data.icons.model.IconPack
import core.data.launchables.model.Launchable
import kotlinx.coroutines.flow.Flow

interface IPreferencesRepository {
    val itemNames: Flow<Preferences>
    val itemIcons: Flow<Preferences>
    fun getItemName(item: Launchable): Flow<String>
    fun setItemName(item: Launchable, name: String)
    fun getIconPackName(): Flow<String?>
    fun setIconPack(iconPack: IconPack)
}
