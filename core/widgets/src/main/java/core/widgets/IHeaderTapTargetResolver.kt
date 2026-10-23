package core.widgets

import core.data.launchables.model.Launchable
import core.data.prefs.HeaderSettings
import core.data.prefs.HeaderTapSetting

/** Resolves the stored Tap actions into what each Header line opens and what the settings screen shows. */
interface IHeaderTapTargetResolver {
    /** Rewrites a formatted Header's tap targets from [settings]. */
    fun applyTo(header: HeaderModel, settings: HeaderSettings, launchables: List<Launchable>, atEpochMillis: Long): HeaderModel

    /**
     * Resolves a stored Tap action for display: a Launchable that no longer exists falls back to the system default
     * once Launchables have loaded.
     */
    fun resolveSetting(setting: HeaderTapSetting, launchables: List<Launchable>, isLoaded: Boolean): HeaderTapSetting
}
