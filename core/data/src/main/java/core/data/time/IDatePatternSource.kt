package core.data.time

import java.util.Locale

interface IDatePatternSource {
    /** The locale's best pattern for [skeleton], as accepted by [android.text.format.DateFormat.getBestDateTimePattern]. */
    fun bestPattern(skeleton: String, locale: Locale): String
}
