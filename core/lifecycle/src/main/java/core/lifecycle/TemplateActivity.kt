package core.lifecycle

import android.app.PendingIntent
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.core.app.TaskStackBuilder
import androidx.lifecycle.lifecycleScope
import core.data.homerole.IHomeRoleSource
import core.data.prefs.IPreferencesRepository
import core.data.prefs.Settings
import core.data.profiles.IProfileSource
import core.ui.layout.resolveRequestedOrientation
import core.ui.model.data.LAUNCHER_SCHEME
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

open class TemplateActivity : ComponentActivity() {
    private val homeRoleSource: IHomeRoleSource by inject()
    private val profileSource: IProfileSource by inject()
    private val preferencesRepository: IPreferencesRepository by inject()
    private var rotateHome: Boolean? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        updateRequestedOrientation(smallestScreenWidthDp = resources.configuration.smallestScreenWidthDp)
        super.onCreate(savedInstanceState)
        observeRotateHome()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateRequestedOrientation(smallestScreenWidthDp = newConfig.smallestScreenWidthDp)
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            homeRoleSource.refreshHomeRole()
            profileSource.refreshProfiles()
        }
    }

    private fun observeRotateHome() {
        lifecycleScope.launch {
            preferencesRepository.settings
                .map { settings: Settings -> settings.rotateHome }
                .distinctUntilChanged()
                .collect { storedRotateHome: Boolean? ->
                    rotateHome = storedRotateHome
                    updateRequestedOrientation()
                }
        }
    }

    private fun updateRequestedOrientation(smallestScreenWidthDp: Int = resources.configuration.smallestScreenWidthDp) {
        requestedOrientation = resolveRequestedOrientation(
            smallestScreenWidthDp = smallestScreenWidthDp,
            rotateHome = rotateHome
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        // Resend intent updates as pending intents to fix navigation
        val data: Uri? = intent.data
        if (data?.scheme == LAUNCHER_SCHEME) {
            val pending: PendingIntent? = TaskStackBuilder.create(this).run {
                addNextIntentWithParentStack(intent)
                getPendingIntent(
                    0,
                    PendingIntent.FLAG_UPDATE_CURRENT.or(PendingIntent.FLAG_IMMUTABLE)
                )
            }
            pending?.send()
        }
    }
}
