package feature.rail

import android.content.Context
import androidx.lifecycle.ViewModel
import core.apprepo.AppRepository

class LauncherViewModel(context: Context, private val appRepository: AppRepository) : ViewModel() {
    val apps = appRepository.apps
}
