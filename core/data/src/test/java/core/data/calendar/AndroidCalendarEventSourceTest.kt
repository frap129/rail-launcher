package core.data.calendar

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.CalendarContract
import app.cash.turbine.test
import core.data.testing.TEST_SDK
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

private const val MILLIS_PER_MINUTE: Long = 60_000L
private const val LOOKAHEAD_HOURS: Int = 2

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [TEST_SDK])
class AndroidCalendarEventSourceTest {
    @Test
    fun `advances the query window from the clock on every recheck`() = runTest {
        val context: Context = RuntimeEnvironment.getApplication()
        val provider: RecordingCalendarProvider = RecordingCalendarProvider()
        ShadowContentResolver.registerProviderInternal(CalendarContract.AUTHORITY, provider)
        val clock: MutableClock = MutableClock(
            current = Instant.parse("2026-10-22T23:50:00Z"),
            zone = ZoneId.of("UTC")
        )
        val source: AndroidCalendarEventSource = AndroidCalendarEventSource(
            context = context,
            clock = clock,
            queryDispatcher = StandardTestDispatcher(testScheduler)
        )

        source.events(lookaheadHours = LOOKAHEAD_HOURS).test {
            runCurrent()
            clock.current = Instant.parse("2026-10-23T00:05:00Z")
            advanceTimeBy(EVENT_RECHECK_INTERVAL_MS + MILLIS_PER_MINUTE)
            runCurrent()

            assertEquals(
                listOf(
                    EventQueryWindow(
                        startMillis = Instant.parse("2026-10-22T00:00:00Z").toEpochMilli(),
                        endMillis = Instant.parse("2026-10-23T01:50:00Z").toEpochMilli()
                    ),
                    EventQueryWindow(
                        startMillis = Instant.parse("2026-10-23T00:00:00Z").toEpochMilli(),
                        endMillis = Instant.parse("2026-10-23T02:05:00Z").toEpochMilli()
                    )
                ),
                provider.queriedWindows()
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    private class MutableClock(var current: Instant, private val zone: ZoneId) : Clock() {
        override fun getZone(): ZoneId = zone

        override fun withZone(zone: ZoneId): Clock = MutableClock(current = current, zone = zone)

        override fun instant(): Instant = current
    }

    private class RecordingCalendarProvider : ContentProvider() {
        private val querySelectionArguments: MutableList<List<String>> = mutableListOf()

        override fun onCreate(): Boolean = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?
        ): Cursor {
            querySelectionArguments.add(selectionArgs?.toList().orEmpty())
            val columns: Array<String> = projection?.map { column -> column }?.toTypedArray() ?: emptyArray()
            return MatrixCursor(columns)
        }

        override fun getType(uri: Uri): String? = null

        override fun insert(uri: Uri, values: ContentValues?): Uri? = null

        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

        fun queriedWindows(): List<EventQueryWindow> = querySelectionArguments.map { arguments ->
            EventQueryWindow(
                startMillis = arguments[1].toLong(),
                endMillis = arguments[0].toLong()
            )
        }
    }
}
