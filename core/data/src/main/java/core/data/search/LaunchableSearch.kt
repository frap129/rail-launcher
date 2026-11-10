package core.data.search

import core.data.apps.model.isPrivateApp
import core.data.launchables.LaunchableState
import core.data.launchables.model.Launchable
import core.data.launchables.model.LaunchableGroup
import core.util.normalizedForSearch
import core.util.wordsForSearch

private const val SINGLE_CHARACTER_QUERY_LENGTH: Int = 1

/** The match strength of a Launchable against a Search query, strongest first. */
private enum class SearchMatchTier {
    Exact,
    NameStart,
    WordStart
}

private fun Launchable.matchTier(query: String): SearchMatchTier? {
    val normalizedName: String = name.normalizedForSearch()
    return when {
        normalizedName == query -> SearchMatchTier.Exact

        normalizedName.startsWith(query) -> SearchMatchTier.NameStart

        query.length > SINGLE_CHARACTER_QUERY_LENGTH && normalizedName.wordsForSearch().any { word -> word.startsWith(query) } ->
            SearchMatchTier.WordStart

        else -> null
    }
}

private fun LaunchableState.hasUnlockedPrivateSpace(): Boolean =
    groups.any { group -> group is LaunchableGroup.PrivateSpace && !group.isLocked }

private fun LaunchableState.isSearchable(launchable: Launchable): Boolean = !launchable.isPrivateApp || hasUnlockedPrivateSpace()

// App List order, then the Launchables kept out of it, such as Apps held in a Folder.
private fun LaunchableState.searchCandidates(): List<Launchable> {
    val listedLaunchables: List<Launchable> = listedGroups.flatMap { group -> group.items }
    val listedKeys: Set<String> = listedLaunchables.mapTo(mutableSetOf()) { launchable -> launchable.key }
    return (listedLaunchables + launchables.filterNot { launchable -> launchable.key in listedKeys })
        .filter { launchable -> isSearchable(launchable = launchable) }
}

/**
 * The Search projection: matches [query] against the resolved name of every Launchable in App
 * List order, strongest match first. Exact names rank above names starting with the query, which
 * rank above names with a word starting with it; a single-character query only uses the first
 * two tiers. Equal matches keep the App List order. Private Apps are only searchable while their
 * Private Space is Unlocked.
 */
fun LaunchableState.searchLaunchables(query: String): List<Launchable> {
    val normalizedQuery: String = query.normalizedForSearch().trim()
    if (normalizedQuery.isEmpty()) {
        return emptyList()
    }
    return searchCandidates()
        .mapNotNull { launchable ->
            launchable.matchTier(query = normalizedQuery)?.let { tier -> tier to launchable }
        }
        .sortedBy { (tier, _) -> tier.ordinal }
        .map { (_, launchable) -> launchable }
}
