package core.data.testing

import core.data.prefs.DateFormatPreset
import core.data.time.IDatePatternSource
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** The locale patterns the Date-pattern sources answer with unless overridden. */
private val STANDARD_DATE_PATTERNS: Map<String, String> = mapOf(
    "hm" to "h:mm a",
    "Hm" to "HH:mm",
    DateFormatPreset.FULL.skeleton to "EEEE, MMMM d",
    DateFormatPreset.SHORT.skeleton to "EEE, MMM d",
    DateFormatPreset.DAY_AND_MONTH.skeleton to "MMMM d",
    DateFormatPreset.WEEKDAY_AND_DAY.skeleton to "EEEE d",
    DateFormatPreset.WEEKDAY.skeleton to "EEEE",
    DateFormatPreset.NUMERIC.skeleton to "M/d/y"
)

/**
 * A MockK [IDatePatternSource] answering [overrides] over [STANDARD_DATE_PATTERNS] and appending
 * every requested skeleton to [requestedSkeletons].
 */
fun createDatePatternSourceMock(
    overrides: Map<String, String> = emptyMap(),
    requestedSkeletons: MutableStateFlow<List<String>> = MutableStateFlow(emptyList())
): IDatePatternSource {
    val patterns: Map<String, String> = STANDARD_DATE_PATTERNS + overrides
    val mockDatePatternSource: IDatePatternSource = mockk()
    every { mockDatePatternSource.bestPattern(any(), any()) } answers {
        val skeleton: String = firstArg()
        requestedSkeletons.update { current -> current + skeleton }
        patterns[skeleton] ?: skeleton
    }
    return mockDatePatternSource
}
