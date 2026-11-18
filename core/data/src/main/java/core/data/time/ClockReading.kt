package core.data.time

import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * Everything the Header needs to render the current time and date, with the device's [locale],
 * which also keys the App List's Initial groups.
 */
data class ClockReading(val instant: Instant, val zoneId: ZoneId, val locale: Locale, val isSystem24Hour: Boolean)
