package core.data.launcher

import androidx.datastore.preferences.core.stringPreferencesKey
import core.data.apps.AppRepository
import core.data.prefs.PreferencesRepository
import java.util.SortedMap
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.combineTransform
import kotlinx.coroutines.flow.mapLatest

class LauncherItemRepository(appRepository: AppRepository, preferencesRepository: PreferencesRepository) {
    private val launcherItemsList = combine(appRepository.apps) { itemLists: Array<List<LauncherItem>> ->
        mutableListOf<LauncherItem>().apply {
            itemLists.forEach { list -> addAll(list) }
        }
    }

    val launcherItems: Flow<SortedMap<Char, List<LauncherItem>>> =
        combineTransform(launcherItemsList, preferencesRepository.itemNames) { items, names ->
            emit(
                items.groupBy { item ->
                    val name = names[stringPreferencesKey(item.key)] ?: item.defaultName
                    item.name.value = name
                    name.first().uppercaseChar()
                }.toSortedMap()
            )
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getItemByKey(key: String): Flow<LauncherItem?> = launcherItemsList.mapLatest { items ->
        items.find { it.key == key }
    }
}
