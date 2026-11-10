package core.data.search

import core.data.apps.model.isPrivateApp
import core.data.launchables.LaunchableState
import core.data.launchables.model.Launchable
import core.data.launchables.model.LaunchableGroup
import core.data.profiles.isPrivate
import core.data.shortcuts.ShortcutLaunchable
import core.util.normalizedForSearch
import core.util.wordsForSearch
import kotlin.math.abs

/** The longest query that only matches exact names and name starts. */
private const val SINGLE_CHARACTER_QUERY_LENGTH: Int = 1

/** The shortest query that matches a Shortcut. */
private const val SHORTCUT_MIN_QUERY_LENGTH: Int = 2

/** The shortest query that matches inside a word. */
private const val IN_WORD_MIN_QUERY_LENGTH: Int = 3

/** The shortest query that tolerates one typo. */
private const val TYPO_MIN_QUERY_LENGTH: Int = 5

/** The shortest query that tolerates two typos. */
private const val DOUBLE_TYPO_MIN_QUERY_LENGTH: Int = 8

/** The edits a query of [TYPO_MIN_QUERY_LENGTH] characters may contain. */
private const val SINGLE_TYPO_EDITS: Int = 1

/** The edits a query of [DOUBLE_TYPO_MIN_QUERY_LENGTH] characters may contain. */
private const val DOUBLE_TYPO_EDITS: Int = 2

/** The match strength of a Launchable against a Search query, strongest first. */
private enum class SearchMatchTier {
    Exact,
    NameStart,
    WordStart,
    Initials,
    InWord,
    Typo
}

/** A Launchable matched at [tier], and whether it is a Shortcut result rather than a Launchable one. */
private data class RankedSearchResult(val tier: SearchMatchTier, val isShortcut: Boolean, val launchable: Launchable)

/** A Launchable about to be matched, with the names Search may match folded once for this query. */
private data class SearchCandidate(val launchable: Launchable, val normalizedNames: List<String>)

private fun Launchable.toSearchCandidate(): SearchCandidate {
    val candidateNames: List<String> = if (hasOriginalName) listOf(name, defaultName).distinct() else listOf(name)
    val normalizedNames: List<String> = candidateNames.map { candidateName -> candidateName.normalizedForSearch() }.distinct()
    return SearchCandidate(launchable = this, normalizedNames = normalizedNames)
}

private fun SearchCandidate.matchTier(query: String): SearchMatchTier? = normalizedNames
    .mapNotNull { normalizedName -> tierFor(normalizedName = normalizedName, query = query) }
    .minByOrNull { tier -> tier.ordinal }

private fun tierFor(normalizedName: String, query: String): SearchMatchTier? {
    if (normalizedName == query) {
        return SearchMatchTier.Exact
    }
    if (normalizedName.startsWith(query)) {
        return SearchMatchTier.NameStart
    }
    if (query.length <= SINGLE_CHARACTER_QUERY_LENGTH) {
        return null
    }
    val words: List<String> = normalizedName.wordsForSearch()
    if (words.any { word -> word.startsWith(query) }) {
        return SearchMatchTier.WordStart
    }
    if (matchesInitials(words = words, query = query)) {
        return SearchMatchTier.Initials
    }
    if (query.length >= IN_WORD_MIN_QUERY_LENGTH && words.any { word -> word.indexOf(query) > 0 }) {
        return SearchMatchTier.InWord
    }
    if (query.length >= TYPO_MIN_QUERY_LENGTH && matchesTypo(normalizedName = normalizedName, words = words, query = query)) {
        return SearchMatchTier.Typo
    }
    return null
}

/** Whether [query] spells the first letters of consecutive [words], starting at any of them. */
private fun matchesInitials(words: List<String>, query: String): Boolean = words.indices.any { firstWordIndex ->
    query.indices.all { queryIndex ->
        words.getOrNull(firstWordIndex + queryIndex)?.firstOrNull() == query[queryIndex]
    }
}

/** Whether [query] is within the edit distance its length allows of the name or of one of its words. */
private fun matchesTypo(normalizedName: String, words: List<String>, query: String): Boolean {
    val maxEdits: Int = if (query.length >= DOUBLE_TYPO_MIN_QUERY_LENGTH) DOUBLE_TYPO_EDITS else SINGLE_TYPO_EDITS
    return words.any { word -> word.isWithinEditDistance(other = query, maxEdits = maxEdits) } ||
        normalizedName.isWithinEditDistance(other = query, maxEdits = maxEdits)
}

/**
 * Whether this text is at most [maxEdits] insertions, deletions, substitutions or adjacent
 * transpositions from [other].
 */
private fun String.isWithinEditDistance(other: String, maxEdits: Int): Boolean {
    if (abs(length - other.length) > maxEdits) {
        return false
    }
    var rowBeforePrevious: IntArray = IntArray(other.length + 1)
    var previousRow: IntArray = IntArray(other.length + 1) { index -> index }
    var currentRow: IntArray = IntArray(other.length + 1)
    for (thisIndex in 1..length) {
        currentRow[0] = thisIndex
        for (otherIndex in 1..other.length) {
            val substitutionCost: Int = if (this[thisIndex - 1] == other[otherIndex - 1]) 0 else 1
            currentRow[otherIndex] = minOf(
                previousRow[otherIndex] + 1,
                currentRow[otherIndex - 1] + 1,
                previousRow[otherIndex - 1] + substitutionCost
            )
            val isTransposition: Boolean = thisIndex > 1 &&
                otherIndex > 1 &&
                this[thisIndex - 1] == other[otherIndex - 2] &&
                this[thisIndex - 2] == other[otherIndex - 1]
            if (isTransposition) {
                currentRow[otherIndex] = minOf(currentRow[otherIndex], rowBeforePrevious[otherIndex - 2] + 1)
            }
        }
        if (currentRow.min() > maxEdits) {
            return false
        }
        val recycledRow: IntArray = rowBeforePrevious
        rowBeforePrevious = previousRow
        previousRow = currentRow
        currentRow = recycledRow
    }
    return previousRow[other.length] <= maxEdits
}

private fun LaunchableState.hasUnlockedPrivateSpace(): Boolean =
    groups.any { group -> group is LaunchableGroup.PrivateSpace && !group.isLocked }

private fun Launchable.isPrivateLaunchable(): Boolean = isPrivateApp || (this is ShortcutLaunchable && profileInfo?.isPrivate == true)

private fun LaunchableState.isSearchable(launchable: Launchable): Boolean = !launchable.isPrivateLaunchable() || hasUnlockedPrivateSpace()

// App List order, then the Launchables kept out of it, such as Apps held in a Folder.
internal fun LaunchableState.searchCandidates(): List<Launchable> {
    val listedLaunchables: List<Launchable> = listedGroups.flatMap { group -> group.items }
    val listedKeys: Set<String> = listedLaunchables.mapTo(mutableSetOf()) { launchable -> launchable.key }
    return (listedLaunchables + launchables.filterNot { launchable -> launchable.key in listedKeys })
        .filter { launchable -> isSearchable(launchable = launchable) }
}

/**
 * The Search projection: matches [query] against the resolved name and, when it has one, the
 * original name of every Launchable in App List order, then against the Shortcuts in [shortcuts].
 * Exact names rank above names starting with the query, then names with a word starting with it,
 * initials, matches inside a word and finally typo-tolerant matches. A single-character query only
 * uses the first two tiers and never matches a Shortcut; a query matches a Shortcut from
 * [SHORTCUT_MIN_QUERY_LENGTH] characters, matches inside a word from
 * [IN_WORD_MIN_QUERY_LENGTH] characters and is within [SINGLE_TYPO_EDITS] edit from
 * [TYPO_MIN_QUERY_LENGTH] (an adjacent transposition counts as one edit) or [DOUBLE_TYPO_EDITS]
 * edits from [DOUBLE_TYPO_MIN_QUERY_LENGTH]. Hidden and disabled Shortcuts never match, and a
 * Shortcut already listed as its own Launchable is left to that Launchable. A Shortcut result
 * carries its publishing App's name in its `publisherName`. Within a tier,
 * Launchables rank above Shortcuts, Launchables with more launches recorded from Search in
 * [launchCounts] rank first, and equal matches keep the App List or source order. Private Apps
 * are only searchable while their Private Space is Unlocked.
 */
fun LaunchableState.searchResults(
    query: String,
    launchCounts: Map<String, Int> = emptyMap(),
    shortcuts: List<SearchShortcut> = emptyList()
): List<Launchable> {
    val normalizedQuery: String = query.normalizedForSearch().trim()
    if (normalizedQuery.isEmpty()) {
        return emptyList()
    }
    val launchableCandidates: List<Launchable> = searchCandidates()
    val launchableKeys: Set<String> = launchableCandidates.mapTo(mutableSetOf()) { launchable -> launchable.key }
    val launchableMatches: List<RankedSearchResult> = launchableCandidates.mapNotNull { launchable ->
        launchable.toSearchCandidate().matchTier(query = normalizedQuery)?.let { tier ->
            RankedSearchResult(tier = tier, isShortcut = false, launchable = launchable)
        }
    }
    val shortcutMatches: List<RankedSearchResult> = if (normalizedQuery.length >= SHORTCUT_MIN_QUERY_LENGTH) {
        shortcuts.asSequence()
            .filter { searchShortcut -> isSearchable(launchable = searchShortcut.shortcut) }
            .filter { searchShortcut -> !searchShortcut.isHidden && !searchShortcut.shortcut.isDisabled }
            .filter { searchShortcut -> searchShortcut.shortcut.key !in launchableKeys }
            .mapNotNull { searchShortcut ->
                searchShortcut.shortcut.toSearchCandidate().matchTier(query = normalizedQuery)?.let { tier ->
                    RankedSearchResult(
                        tier = tier,
                        isShortcut = true,
                        launchable = searchShortcut.shortcut.copy(publisherName = searchShortcut.publisherName)
                    )
                }
            }
            .toList()
    } else {
        emptyList()
    }
    return (launchableMatches + shortcutMatches)
        .sortedWith(
            compareBy<RankedSearchResult> { match -> match.tier.ordinal }
                .thenBy { match -> match.isShortcut }
                .thenByDescending { match -> launchCounts[match.launchable.key] ?: 0 }
        )
        .map { match -> match.launchable }
}
