package feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import core.data.prefs.PreferencesRepository

class SettingsViewModel(context: Context, private val prefsRepo: PreferencesRepository) : ViewModel()
