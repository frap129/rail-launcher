package core.data.testing

import core.data.time.IDatePatternSource
import java.util.Locale

class FakeDatePatternSource(
    private val patterns: Map<String, String> = mapOf(
        "hm" to "h:mm a",
        "Hm" to "HH:mm",
        "EEEEMMMMd" to "EEEE, MMMM d"
    )
) : IDatePatternSource {
    override fun bestPattern(skeleton: String, locale: Locale): String = patterns[skeleton] ?: skeleton
}
