package dev.maples.rail

import android.app.WallpaperManager
import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import core.lifecycle.HomeSignals
import core.lifecycle.TemplateActivity
import core.ui.App
import core.ui.model.data.Destination
import feature.launcher.launcherDestination
import feature.launcher.widgets.AppWidgetResults
import feature.settings.settingsDestination
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject

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

    private val wallpaperManager: WallpaperManager by lazy {
        WallpaperManager.getInstance(applicationContext)
    }

    private var isWallpaperBacked: Boolean = launcherDestination.isWallpaperBacked

    // Replaced by App's reported tone before a non-wallpaper destination is shown.
    private var isSurfaceDark: Boolean = true
    private var wallpaperColorHints: Int? = null
    private var hasReceivedWallpaperColors: Boolean = false

    private val wallpaperColorsListener = WallpaperManager.OnColorsChangedListener { colors, which ->
        if ((which and WallpaperManager.FLAG_SYSTEM) != 0) {
            hasReceivedWallpaperColors = true
            wallpaperColorHints = colors?.colorHints
            updateSystemBarIconAppearance()
        }
    }

    private val appWidgetResults: AppWidgetResults by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = wallpaperFollowingSystemBarStyle(),
            navigationBarStyle = wallpaperFollowingSystemBarStyle()
        )
        updateSystemBarIconAppearance()

        super.onCreate(savedInstanceState)

        observeWallpaperColors()
        setContent {
            App(
                destinations = destinations,
                startDestination = launcherDestination.route,
                homeSignals = homeSignals.events,
                onDestinationChanged = ::onDestinationChanged
            )
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateSystemBarIconAppearance()
        // The activity callback runs before view-tree config dispatch, where androidx
        // EdgeToEdge re-runs its setup and re-enables the contrast scrim.
        window.decorView.post { window.isNavigationBarContrastEnforced = false }
    }

    override fun onDestroy() {
        wallpaperManager.removeOnColorsChangedListener(wallpaperColorsListener)
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            homeSignals.signal()
        }
    }

    private fun observeWallpaperColors() {
        wallpaperManager.addOnColorsChangedListener(wallpaperColorsListener, Handler(Looper.getMainLooper()))
        // getWallpaperColors can block while the system extracts colors, so keep it off the
        // main thread; SystemBarStyle.auto covers the first frames until the hints arrive.
        lifecycleScope.launch {
            val colorHints: Int? = withContext(Dispatchers.IO) {
                wallpaperManager.getWallpaperColors(WallpaperManager.FLAG_SYSTEM)?.colorHints
            }
            // A listener callback that landed while the read was in flight is fresher.
            if (!hasReceivedWallpaperColors) {
                wallpaperColorHints = colorHints
                updateSystemBarIconAppearance()
            }
        }
    }

    private fun onDestinationChanged(isWallpaperBacked: Boolean, isSurfaceDark: Boolean) {
        this.isWallpaperBacked = isWallpaperBacked
        this.isSurfaceDark = isSurfaceDark
        updateSystemBarIconAppearance()
    }

    private fun shouldUseDarkSystemBarIconsForCurrentDestination(resources: Resources): Boolean = shouldUseDarkSystemBarIcons(
        isWallpaperBacked = isWallpaperBacked,
        isSurfaceDark = isSurfaceDark,
        wallpaperColorHints = wallpaperColorHints,
        isNightMode = resources.isNightMode()
    )

    private fun updateSystemBarIconAppearance() {
        // SystemBarStyle.auto turns the navigation bar contrast scrim back on whenever
        // edge-to-edge setup re-runs; the launcher must keep showing the wallpaper.
        window.isNavigationBarContrastEnforced = false
        val useDarkIcons: Boolean = shouldUseDarkSystemBarIconsForCurrentDestination(resources)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = useDarkIcons
            isAppearanceLightNavigationBars = useDarkIcons
        }
    }

    private fun wallpaperFollowingSystemBarStyle(): SystemBarStyle =
        SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { resources ->
            !shouldUseDarkSystemBarIconsForCurrentDestination(resources)
        }

    private fun Resources.isNightMode(): Boolean =
        (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        appWidgetResults.onActivityResult(requestCode = requestCode, resultCode = resultCode)
        super.onActivityResult(requestCode, resultCode, data)
    }
}
