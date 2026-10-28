package core.ui.widgets

import core.data.launchables.LaunchableState
import core.data.prefs.HeaderSettings
import core.data.prefs.HeaderTapSetting

/** Resolves the stored Tap actions into what each Header line opens and what the settings screen shows. */
interface IHeaderTapTargetResolver {
    /** Rewrites a formatted Header's tap targets from [settings]. */
    fun applyTo(header: HeaderModel, settings: HeaderSettings, launchableState: LaunchableState, atEpochMillis: Long): HeaderModel

    /**
     * Resolves a stored Tap action for display: a Launchable that no longer exists, or one in a
     * Locked Profile, falls back to the system default once Launchables have loaded.
     */
    fun resolveSetting(setting: HeaderTapSetting, launchableState: LaunchableState): HeaderTapSetting
}
