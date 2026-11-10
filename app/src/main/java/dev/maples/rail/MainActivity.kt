package dev.maples.rail

import android.app.WallpaperManager
import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import core.lifecycle.HomePressState
import core.lifecycle.LauncherIntentHandler
import core.lifecycle.LauncherKeyHandler
import core.lifecycle.LauncherSignals
import core.lifecycle.TemplateActivity
import core.lifecycle.isSearchEntry
import core.ui.App
import core.ui.model.data.Destination
import core.ui.model.data.LauncherTypingState
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
     * Delivers launcher signals to the UI so a re-selection shows Home
     * scrolled to the top and the Search entry opens Search.
     *
     * @property launcherSignals the activity-owned [core.lifecycle.LauncherSignals] queue
     */
    private val launcherSignals: LauncherSignals = LauncherSignals()

    private val launcherIntentHandler: LauncherIntentHandler = LauncherIntentHandler(onSignal = launcherSignals::signal)

    private val launcherTypingState: LauncherTypingState by inject()

    private val wallpaperManager: WallpaperManager by lazy {
        WallpaperManager.getInstance(applicationContext)
    }

    private val homePressState: HomePressState = HomePressState()

    private var isWallpaperBacked: Boolean = launcherDestination.isWallpaperBacked
    private var isLauncherShown: Boolean = true

    private val launcherKeyHandler: LauncherKeyHandler = LauncherKeyHandler(
        isLauncherShown = { isLauncherShown },
        isTypingAccepted = { launcherTypingState.acceptsTyping.value },
        onBack = { onBackPressedDispatcher.onBackPressed() },
        onSignal = launcherSignals::signal
    )

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

        launcherIntentHandler.restore(savedInstanceState = savedInstanceState)
        launcherIntentHandler.handle(intent = intent, wasResumed = false, isNewIntent = false)

        observeWallpaperColors()
        setContent {
            App(
                destinations = destinations,
                startDestination = launcherDestination.route,
                launcherSignals = launcherSignals.events,
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

    override fun onPause() {
        super.onPause()
        homePressState.onPause()
    }

    override fun onResume() {
        super.onResume()
        homePressState.onResume()
    }

    override fun onStop() {
        super.onStop()
        homePressState.onStop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        homePressState.onWindowFocusChanged(hasFocus = hasFocus)
    }

    override fun onNewIntent(intent: Intent) {
        setIntent(intent)
        launcherIntentHandler.handle(
            intent = intent,
            wasResumed = homePressState.wasResumedAtHomeIntent(),
            isNewIntent = true
        )
        // Only the Search entry skips TemplateActivity's pending-intent resend.
        if (!intent.isSearchEntry()) {
            super.onNewIntent(intent)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        launcherIntentHandler.save(outState = outState)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean =
        launcherKeyHandler.onKeyDown(keyCode = keyCode, event = event) || super.onKeyDown(keyCode, event)

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

    private fun onDestinationChanged(route: String?, isWallpaperBacked: Boolean, isSurfaceDark: Boolean) {
        isLauncherShown = route == launcherDestination.route
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
