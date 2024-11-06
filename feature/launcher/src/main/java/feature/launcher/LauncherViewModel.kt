package feature.launcher

import android.content.Context
import androidx.lifecycle.ViewModel
import core.data.apps.AppRepository
import core.data.rail.RailItem
import java.util.SortedMap
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapLatest

class LauncherViewModel(context: Context, private val appRepository: AppRepository) : ViewModel() {
    @OptIn(ExperimentalCoroutinesApi::class)
    val launcherItems: Flow<SortedMap<Char, List<RailItem>>> = appRepository.apps.mapLatest { appList ->
        appList.groupBy { it.name[0].uppercaseChar() }.toSortedMap()
    }
}
