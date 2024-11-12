package core.data.launcher

import core.data.apps.AppRepository
import kotlinx.coroutines.flow.combine

class LauncherItemRepository(appRepository: AppRepository) {
    val launcherItems = combine(appRepository.apps) { itemLists: Array<List<LauncherItem>> ->
        var itemMap: Map<Char, List<LauncherItem>> = mapOf()
        itemLists.forEach { list ->
            itemMap = itemMap.plus(list.groupBy { it.name.first().uppercaseChar() })
        }
        itemMap.toSortedMap()
    }
}
