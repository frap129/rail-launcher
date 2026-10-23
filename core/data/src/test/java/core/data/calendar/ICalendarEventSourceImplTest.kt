package core.data.calendar

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Looper
import android.provider.CalendarContract
import app.cash.turbine.test
import core.data.testing.TEST_SDK
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

private const val MILLIS_PER_MINUTE: Long = 60_000L
private const val LOOKAHEAD_HOURS: Int = 2

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [TEST_SDK])
class ICalendarEventSourceImplTest {
    @Test
    fun `rolls the window across midnight and emits tomorrow events`() = runTest {
        val inputContext: Context = RuntimeEnvironment.getApplication()
        val inputTomorrowEvent: UpcomingEvent = event(
            eventId = 1,
            title = "Tomorrow",
            begin = "2026-10-23T02:00:00Z",
            end = "2026-10-23T02:30:00Z"
        )
        val mockProvider: RecordingCalendarProvider = RecordingCalendarProvider(events = listOf(inputTomorrowEvent))
        ShadowContentResolver.registerProviderInternal(CalendarContract.AUTHORITY, mockProvider)
        val mockTime: MutableTime = MutableTime(
            current = Instant.parse("2026-10-22T23:50:00Z"),
            zone = ZoneId.of("UTC")
        )
        val source: ICalendarEventSourceImpl = createSource(context = inputContext, time = mockTime, scheduler = testScheduler)

        source.events(lookaheadHours = LOOKAHEAD_HOURS).test {
            runCurrent()
            assertEquals(emptyList<UpcomingEvent>(), awaitItem())

            mockTime.current = Instant.parse("2026-10-23T00:05:00Z")
            advanceTimeBy(EVENT_RECHECK_INTERVAL_MS + MILLIS_PER_MINUTE)
            runCurrent()

            assertEquals(
                listOf(
                    window(start = "2026-10-22T00:00:00Z", end = "2026-10-23T01:50:00Z"),
                    window(start = "2026-10-23T00:00:00Z", end = "2026-10-23T02:05:00Z")
                ),
                mockProvider.queriedWindows()
            )
            assertEquals(listOf(inputTomorrowEvent), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `rechecks the window on a calendar content change`() = runTest {
        val inputContext: Context = RuntimeEnvironment.getApplication()
        val mockProvider: RecordingCalendarProvider = RecordingCalendarProvider()
        ShadowContentResolver.registerProviderInternal(CalendarContract.AUTHORITY, mockProvider)
        val mockTime: MutableTime = MutableTime(
            current = Instant.parse("2026-10-22T23:50:00Z"),
            zone = ZoneId.of("UTC")
        )
        val source: ICalendarEventSourceImpl = createSource(context = inputContext, time = mockTime, scheduler = testScheduler)

        source.events(lookaheadHours = LOOKAHEAD_HOURS).test {
            runCurrent()
            assertEquals(emptyList<UpcomingEvent>(), awaitItem())

            mockTime.current = Instant.parse("2026-10-23T00:05:00Z")
            inputContext.contentResolver.notifyChange(CalendarContract.Instances.CONTENT_URI, null)
            shadowOf(Looper.getMainLooper()).idle()
            runCurrent()

            assertEquals(
                listOf(
                    window(start = "2026-10-22T00:00:00Z", end = "2026-10-23T01:50:00Z"),
                    window(start = "2026-10-23T00:00:00Z", end = "2026-10-23T02:05:00Z")
                ),
                mockProvider.queriedWindows()
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `uses the current time zone on every recheck`() = runTest {
        val inputContext: Context = RuntimeEnvironment.getApplication()
        val mockProvider: RecordingCalendarProvider = RecordingCalendarProvider()
        ShadowContentResolver.registerProviderInternal(CalendarContract.AUTHORITY, mockProvider)
        val mockTime: MutableTime = MutableTime(
            current = Instant.parse("2026-10-22T03:50:00Z"),
            zone = ZoneId.of("UTC")
        )
        val source: ICalendarEventSourceImpl = createSource(context = inputContext, time = mockTime, scheduler = testScheduler)

        source.events(lookaheadHours = LOOKAHEAD_HOURS).test {
            runCurrent()
            assertEquals(emptyList<UpcomingEvent>(), awaitItem())

            mockTime.current = Instant.parse("2026-10-22T05:05:00Z")
            mockTime.zone = ZoneId.of("America/New_York")
            advanceTimeBy(EVENT_RECHECK_INTERVAL_MS + MILLIS_PER_MINUTE)
            runCurrent()

            assertEquals(
                window(start = "2026-10-22T04:00:00Z", end = "2026-10-22T07:05:00Z"),
                mockProvider.queriedWindows().last()
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun createSource(context: Context, time: MutableTime, scheduler: TestCoroutineScheduler): ICalendarEventSourceImpl =
        ICalendarEventSourceImpl(
            context = context,
            now = { time.current },
            zoneId = { time.zone },
            queryDispatcher = StandardTestDispatcher(scheduler)
        )

    private fun window(start: String, end: String): EventQueryWindow = EventQueryWindow(
        startMillis = Instant.parse(start).toEpochMilli(),
        endMillis = Instant.parse(end).toEpochMilli()
    )

    private fun event(eventId: Long, title: String, begin: String, end: String): UpcomingEvent = UpcomingEvent(
        eventId = eventId,
        title = title,
        beginMillis = Instant.parse(begin).toEpochMilli(),
        endMillis = Instant.parse(end).toEpochMilli(),
        isAllDay = false,
        calendarId = 7L,
        color = 42,
        isWorkProfile = false
    )

    private class MutableTime(var current: Instant, var zone: ZoneId)

    private class RecordingCalendarProvider(private val events: List<UpcomingEvent> = emptyList()) : ContentProvider() {
        private val windows: MutableList<EventQueryWindow> = mutableListOf()

        override fun onCreate(): Boolean = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?
        ): Cursor {
            val columns: Array<String> = projection?.map { column -> column }?.toTypedArray() ?: emptyArray()
            val cursor: MatrixCursor = MatrixCursor(columns)
            if (uri != CalendarContract.Instances.CONTENT_URI) return cursor

            val arguments: List<String> = selectionArgs?.toList().orEmpty()
            val window: EventQueryWindow = EventQueryWindow(
                startMillis = arguments[1].toLong(),
                endMillis = arguments[0].toLong()
            )
            windows.add(window)
            events
                .filter { event -> event.beginMillis < window.endMillis && event.endMillis > window.startMillis }
                .forEach { event ->
                    cursor.addRow(
                        columns.map { column ->
                            when (column) {
                                CalendarContract.Instances.EVENT_ID -> event.eventId
                                CalendarContract.Instances.TITLE -> event.title
                                CalendarContract.Instances.BEGIN -> event.beginMillis
                                CalendarContract.Instances.END -> event.endMillis
                                CalendarContract.Instances.ALL_DAY -> if (event.isAllDay) 1 else 0
                                CalendarContract.Instances.CALENDAR_ID -> event.calendarId
                                CalendarContract.Instances.DISPLAY_COLOR -> event.color
                                else -> null
                            }
                        }
                    )
                }
            return cursor
        }

        override fun getType(uri: Uri): String? = null

        override fun insert(uri: Uri, values: ContentValues?): Uri? = null

        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

        fun queriedWindows(): List<EventQueryWindow> = windows
    }
}
