package core.data.testing

import core.data.time.IDatePatternSource
import java.util.Locale

class FakeDatePatternSource(overrides: Map<String, String> = emptyMap()) : IDatePatternSource {
    private val patterns: Map<String, String> = STANDARD_DATE_PATTERNS + overrides

    override fun bestPattern(skeleton: String, locale: Locale): String = patterns[skeleton] ?: skeleton
}
