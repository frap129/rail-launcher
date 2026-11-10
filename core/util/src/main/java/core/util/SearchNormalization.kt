package core.util

import java.text.Normalizer
import java.util.Locale

private val COMBINING_MARKS_REGEX: Regex = Regex("\\p{M}+")

/**
 * The single search fold: this text decomposed with Unicode NFD, stripped of all Unicode marks
 * (`\p{M}`) and lowercased with [Locale.ROOT], so case and accents are ignored. The same strip
 * applies to the marks of Indic, Arabic and Hebrew scripts, while unmarked non-Latin text, such
 * as CJK or Cyrillic, keeps its own characters instead of being transliterated.
 */
fun String.normalizedForSearch(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD).replace(COMBINING_MARKS_REGEX, "").lowercase(Locale.ROOT)

/**
 * A query folded once with [normalizedForSearch], matching candidate text that contains it. A
 * blank query matches every candidate.
 */
class SearchQuery(query: String) {
    private val normalizedQuery: String = query.normalizedForSearch()

    /** Whether [candidate], folded for search, contains this query. */
    fun matches(candidate: String): Boolean = candidate.normalizedForSearch().contains(normalizedQuery)
}
