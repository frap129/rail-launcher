package core.data.time

import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/** Everything the Header needs to render the current time and date. */
data class ClockReading(val instant: Instant, val zoneId: ZoneId, val locale: Locale, val isSystem24Hour: Boolean)
