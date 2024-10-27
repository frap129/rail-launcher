package feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import core.prefsrepo.PreferencesRepository

class SettingsViewModel(context: Context, private val prefsRepo: PreferencesRepository) : ViewModel()
