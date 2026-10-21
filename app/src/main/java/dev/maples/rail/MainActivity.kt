package dev.maples.rail

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import core.lifecycle.HomeSignals
import core.lifecycle.TemplateActivity
import core.ui.App
import core.ui.model.data.Destination
import feature.launcher.launcherDestination
import feature.settings.settingsDestination

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

    /**
     * Delivers HOME intent events to the UI so a re-selection shows Home
     * scrolled to the top.
     *
     * @property homeSignals the activity-owned [core.lifecycle.HomeSignals] queue
     */
    private val homeSignals: HomeSignals = HomeSignals()

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
                homeSignals = homeSignals.events
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            homeSignals.signal()
        }
    }
}
