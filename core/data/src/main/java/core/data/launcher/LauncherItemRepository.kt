package core.data.launcher

import androidx.datastore.preferences.core.stringPreferencesKey
import core.data.apps.AppRepository
import core.data.launcher.model.LauncherItem
import core.data.launcher.model.LauncherItemGroup
import core.data.prefs.PreferencesRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest

class LauncherItemRepository(appRepository: AppRepository, preferencesRepository: PreferencesRepository) {
    private val launcherItems = combine(appRepository.apps) { itemLists: Array<List<LauncherItem>> ->
        mutableListOf<LauncherItem>().apply {
            itemLists.forEach { list -> addAll(list) }
        }
    }

    val launcherItemGroups: Flow<List<LauncherItemGroup>> = combine(launcherItems, preferencesRepository.itemNames) { items, names ->
        items.groupBy { item ->
            val name = names[stringPreferencesKey(item.key)] ?: item.defaultName
            item.name = name
            name.first().uppercaseChar()
        }.map { mapEntry ->
            LauncherItemGroup(
                mapEntry.key.toString(),
                mapEntry.value
            )
        }.sortedBy { it.name }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getItemByKey(key: String): Flow<LauncherItem?> = launcherItems.mapLatest { items ->
        items.find { it.key == key }
    }
}
