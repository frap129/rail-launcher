package core.data.search

import core.data.folders.Folder
import core.data.prefs.Settings

/**
 * The typed Search result Auto-launch opens for [results], or null when it must not fire: the
 * setting is off, there is not exactly one Launchable or Shortcut result, that result is Paused,
 * disabled or a Folder (which opens its Pop-up), or a Calculator or Contact row is showing.
 * External rows never hold it back.
 */
fun List<SearchResult>.autoLaunchResult(settings: Settings): SearchResult.LaunchableResult? {
    if (!settings.searchAutoLaunch) {
        return null
    }
    val launchableResults: List<SearchResult.LaunchableResult> = filterIsInstance<SearchResult.LaunchableResult>()
    if (
        launchableResults.size != 1 ||
        any { result -> result is SearchResult.CalculatorRow || result is SearchResult.ContactRow }
    ) {
        return null
    }
    return launchableResults.single().takeUnless { result ->
        result.launchable.isPaused || result.launchable.isDisabled || result.launchable is Folder
    }
}
