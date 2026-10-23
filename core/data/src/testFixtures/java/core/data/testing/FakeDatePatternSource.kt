package core.data.testing

import core.data.time.IDatePatternSource
import java.util.Locale

class FakeDatePatternSource(overrides: Map<String, String> = emptyMap()) : IDatePatternSource {
    private val patterns: Map<String, String> = DEFAULT_PATTERNS + overrides

    override fun bestPattern(skeleton: String, locale: Locale): String = patterns[skeleton] ?: skeleton

    private companion object {
        private val DEFAULT_PATTERNS: Map<String, String> = mapOf(
            "hm" to "h:mm a",
            "Hm" to "HH:mm",
            "EEEEMMMMd" to "EEEE, MMMM d"
        )
    }
}
