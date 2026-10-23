package core.data.time

import android.text.format.DateFormat
import java.util.Locale

class IDatePatternSourceImpl : IDatePatternSource {
    override fun bestPattern(skeleton: String, locale: Locale): String = DateFormat.getBestDateTimePattern(locale, skeleton)
}
