package core.data.time

import android.text.format.DateFormat
import java.util.Locale

class AndroidDatePatternSource : IDatePatternSource {
    override fun bestPattern(skeleton: String, locale: Locale): String = DateFormat.getBestDateTimePattern(locale, skeleton)
}
