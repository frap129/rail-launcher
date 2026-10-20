package dev.maples.rail

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import core.lifecycle.TemplateActivity
import core.ui.App
import core.ui.model.data.Destination
import feature.launcher.launcherDestination
import feature.settings.settingsDestination
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

private const val HOME_SIGNAL_BUFFER_CAPACITY: Int = 1

class MainActivity : TemplateActivity() {
    /**
     * Holds Destination objects for all navigable screens in the app.
     * This list is consumed by NavHost to add the defined routes to
     * the NavGraph.
     *
     * @property destinations a list of [core.ui.model.data.Destination]
     */
    private val destinations: List<Destination> = listOf(
        launcherDestination,
        settingsDestination
    )
    private val homeSignals: MutableSharedFlow<Unit> = MutableSharedFlow(extraBufferCapacity = HOME_SIGNAL_BUFFER_CAPACITY)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        setContent {
            App(
                destinations = destinations,
                startDestination = launcherDestination.route,
                homeSignals = homeSignals
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) {
            lifecycleScope.launch {
                homeSignals.emit(Unit)
            }
        }
    }
}
