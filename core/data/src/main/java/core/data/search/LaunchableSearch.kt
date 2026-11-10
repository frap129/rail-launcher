package core.data.search

import core.data.apps.model.isPrivateApp
import core.data.launchables.LaunchableState
import core.data.launchables.model.Launchable
import core.data.launchables.model.LaunchableGroup
import core.util.normalizedForSearch
import core.util.wordsForSearch
import kotlin.math.abs

/** The longest query that only matches exact names and name starts. */
private const val SINGLE_CHARACTER_QUERY_LENGTH: Int = 1

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

private fun LaunchableState.isSearchable(launchable: Launchable): Boolean = !launchable.isPrivateApp || hasUnlockedPrivateSpace()

// App List order, then the Launchables kept out of it, such as Apps held in a Folder.
private fun LaunchableState.searchCandidates(): List<Launchable> {
    val listedLaunchables: List<Launchable> = listedGroups.flatMap { group -> group.items }
    val listedKeys: Set<String> = listedLaunchables.mapTo(mutableSetOf()) { launchable -> launchable.key }
    return (listedLaunchables + launchables.filterNot { launchable -> launchable.key in listedKeys })
        .filter { launchable -> isSearchable(launchable = launchable) }
}

/**
 * The Search projection: matches [query] against the resolved name and, when it has one, the
 * original name of every Launchable in App List order, strongest match first. Exact names rank
 * above names starting with the query, then names with a word starting with it, initials,
 * matches inside a word and finally typo-tolerant matches. A single-character query only uses
 * the first two tiers; a query matches inside a word from [IN_WORD_MIN_QUERY_LENGTH] characters
 * and within [SINGLE_TYPO_EDITS] edit from [TYPO_MIN_QUERY_LENGTH] (an adjacent transposition
 * counts as one edit) or [DOUBLE_TYPO_EDITS] edits from [DOUBLE_TYPO_MIN_QUERY_LENGTH]. Equal
 * matches keep the App List order. Private Apps are only searchable while their Private Space is
 * Unlocked.
 */
fun LaunchableState.searchLaunchables(query: String): List<Launchable> {
    val normalizedQuery: String = query.normalizedForSearch().trim()
    if (normalizedQuery.isEmpty()) {
        return emptyList()
    }
    return searchCandidates()
        .map { launchable -> launchable.toSearchCandidate() }
        .mapNotNull { candidate ->
            candidate.matchTier(query = normalizedQuery)?.let { tier -> tier to candidate.launchable }
        }
        .sortedBy { (tier, _) -> tier.ordinal }
        .map { (_, launchable) -> launchable }
}
