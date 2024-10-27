package feature.launcher

import android.content.Context
import androidx.lifecycle.ViewModel
import core.apprepo.App
import core.apprepo.AppRepository
import java.util.SortedMap
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapLatest

class LauncherViewModel(context: Context, private val appRepository: AppRepository) : ViewModel() {
    @OptIn(ExperimentalCoroutinesApi::class)
    val launcherItems: Flow<SortedMap<Char, List<App>>> = appRepository.apps.mapLatest { appList ->
        appList.groupBy { it.label[0].uppercaseChar() }.toSortedMap()
    }

    val heightInPixels = context.resources.displayMetrics.heightPixels
    val heightInDp = heightInPixels / context.resources.displayMetrics.density
}
