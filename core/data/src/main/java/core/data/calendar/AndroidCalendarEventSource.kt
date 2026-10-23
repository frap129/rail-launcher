package core.data.calendar

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber

private const val RECHECK_INTERVAL_MS: Long = 15 * 60 * 1_000L

private enum class CrossProfileCalendarAccess {
    Allowed,
    Denied,
    Unknown
}

class AndroidCalendarEventSource(context: Context) : ICalendarEventSource {
    private val appContext: Context = context.applicationContext
    private val permission: MutableStateFlow<Boolean> = MutableStateFlow(hasCalendarPermission())

    override val hasPermission: StateFlow<Boolean> = permission.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    override val calendars: Flow<List<CalendarInfo>> = permission.flatMapLatest { granted ->
        if (granted) {
            contentObserverFlow(query = ::queryCalendars)
        } else {
            flowOf(emptyList())
        }
    }.distinctUntilChanged()

    override fun events(windowEndMillis: Long): Flow<List<UpcomingEvent>> = callbackFlow {
        val flowScope: ProducerScope<List<UpcomingEvent>> = this
        val windowStartMillis: Long = startOfTodayMillis()
        fun recheck() {
            flowScope.launch(Dispatchers.IO) {
                trySend(queryEvents(windowStartMillis = windowStartMillis, windowEndMillis = windowEndMillis))
            }
        }
        val observer: ContentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                recheck()
            }
        }
        val registeredUris: List<android.net.Uri> = registerObservers(observer = observer, uris = eventUris())
        recheck()
        val ticker: Job = launch {
            while (isActive) {
                delay(RECHECK_INTERVAL_MS)
                recheck()
            }
        }
        awaitClose {
            ticker.cancel()
            registeredUris.forEach { uri -> appContext.contentResolver.unregisterContentObserver(observer) }
        }
    }.distinctUntilChanged()

    override fun refreshPermission() {
        permission.value = hasCalendarPermission()
    }

    private fun hasCalendarPermission(): Boolean = ContextCompat.checkSelfPermission(
        appContext,
        Manifest.permission.READ_CALENDAR
    ) == PackageManager.PERMISSION_GRANTED

    /** Whether a profile other than the current user exists; null when it cannot be determined. */
    private fun hasSecondaryProfile(): Boolean? {
        val userManager: UserManager = appContext.getSystemService(UserManager::class.java) ?: return null
        return try {
            userManager.userProfiles.any { profile -> profile != Process.myUserHandle() }
        } catch (exception: Exception) {
            Timber.w(exception, "Unable to enumerate user profiles")
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun crossProfileCalendarAccess(): CrossProfileCalendarAccess {
        when (hasSecondaryProfile()) {
            false -> return CrossProfileCalendarAccess.Denied
            null -> return CrossProfileCalendarAccess.Unknown
            true -> Unit
        }
        val devicePolicyManager: DevicePolicyManager =
            appContext.getSystemService(DevicePolicyManager::class.java) ?: return CrossProfileCalendarAccess.Unknown
        val admin: ComponentName = deviceOwnerComponent(devicePolicyManager) ?: return CrossProfileCalendarAccess.Unknown
        return try {
            val allowedPackages: Set<String>? = devicePolicyManager.getCrossProfileCalendarPackages(admin)
            if (allowedPackages == null || appContext.packageName in allowedPackages) {
                CrossProfileCalendarAccess.Allowed
            } else {
                CrossProfileCalendarAccess.Denied
            }
        } catch (exception: Exception) {
            Timber.w(exception, "Unable to read cross-profile calendar access")
            CrossProfileCalendarAccess.Unknown
        }
    }

    @Suppress("PrivateApi")
    private fun deviceOwnerComponent(devicePolicyManager: DevicePolicyManager): ComponentName? = try {
        val method: java.lang.reflect.Method = DevicePolicyManager::class.java.getMethod("getDeviceOwnerComponentOnAnyUser")
        method.invoke(devicePolicyManager) as? ComponentName
    } catch (exception: ReflectiveOperationException) {
        null
    } catch (exception: SecurityException) {
        null
    }

    private fun startOfTodayMillis(): Long = LocalDate.now(ZoneId.systemDefault())
        .atStartOfDay(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()

    private fun eventUris(): List<Uri> = buildList {
        add(CalendarContract.Instances.CONTENT_URI)
        if (crossProfileCalendarAccess() != CrossProfileCalendarAccess.Denied) {
            add(CalendarContract.Instances.ENTERPRISE_CONTENT_URI)
        }
    }

    private fun calendarUris(): List<Uri> = buildList {
        add(CalendarContract.Calendars.CONTENT_URI)
        if (crossProfileCalendarAccess() != CrossProfileCalendarAccess.Denied) {
            add(CalendarContract.Calendars.ENTERPRISE_CONTENT_URI)
        }
    }

    private fun queryEvents(windowStartMillis: Long, windowEndMillis: Long): List<UpcomingEvent> = buildList {
        addAll(queryEvents(uri = CalendarContract.Instances.CONTENT_URI, isWorkProfile = false, windowStartMillis, windowEndMillis))
        if (crossProfileCalendarAccess() != CrossProfileCalendarAccess.Denied) {
            addAll(
                queryEvents(
                    uri = CalendarContract.Instances.ENTERPRISE_CONTENT_URI,
                    isWorkProfile = true,
                    windowStartMillis,
                    windowEndMillis
                )
            )
        }
    }

    private fun queryEvents(
        uri: android.net.Uri,
        isWorkProfile: Boolean,
        windowStartMillis: Long,
        windowEndMillis: Long
    ): List<UpcomingEvent> {
        val selection: String = "${CalendarContract.Instances.BEGIN} < ? AND ${CalendarContract.Instances.END} > ? AND " +
            "${CalendarContract.Instances.VISIBLE} = 1 AND " +
            "(${CalendarContract.Instances.SELF_ATTENDEE_STATUS} IS NULL OR " +
            "${CalendarContract.Instances.SELF_ATTENDEE_STATUS} != ?) AND " +
            "(${CalendarContract.Instances.STATUS} IS NULL OR ${CalendarContract.Instances.STATUS} != ?)"
        val selectionArgs: Array<String> = arrayOf(
            windowEndMillis.toString(),
            windowStartMillis.toString(),
            CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED.toString(),
            CalendarContract.Events.STATUS_CANCELED.toString()
        )
        return query(
            uri = uri,
            projection = EVENT_PROJECTION,
            selection = selection,
            selectionArgs = selectionArgs,
            sortOrder = "${CalendarContract.Instances.BEGIN} ASC"
        ) { cursor -> cursor.toEvent(isWorkProfile = isWorkProfile) }
    }

    private fun Cursor.toEvent(isWorkProfile: Boolean): UpcomingEvent = UpcomingEvent(
        eventId = getLong(getColumnIndexOrThrow(CalendarContract.Instances.EVENT_ID)),
        title = getString(getColumnIndexOrThrow(CalendarContract.Instances.TITLE)).orEmpty(),
        beginMillis = getLong(getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)),
        endMillis = getLong(getColumnIndexOrThrow(CalendarContract.Instances.END)),
        isAllDay = getInt(getColumnIndexOrThrow(CalendarContract.Instances.ALL_DAY)) != 0,
        calendarId = getLong(getColumnIndexOrThrow(CalendarContract.Instances.CALENDAR_ID)),
        color = getInt(getColumnIndexOrThrow(CalendarContract.Instances.DISPLAY_COLOR)),
        isWorkProfile = isWorkProfile
    )

    private fun queryCalendars(): List<CalendarInfo> = buildList {
        addAll(queryCalendars(uri = CalendarContract.Calendars.CONTENT_URI, isWorkProfile = false))
        if (crossProfileCalendarAccess() != CrossProfileCalendarAccess.Denied) {
            addAll(queryCalendars(uri = CalendarContract.Calendars.ENTERPRISE_CONTENT_URI, isWorkProfile = true))
        }
    }

    private fun queryCalendars(uri: android.net.Uri, isWorkProfile: Boolean): List<CalendarInfo> = query(
        uri = uri,
        projection = if (isWorkProfile) WORK_CALENDAR_PROJECTION else CALENDAR_PROJECTION,
        selection = "${CalendarContract.Calendars.VISIBLE} = 1",
        selectionArgs = null,
        sortOrder = if (isWorkProfile) null else "${CalendarContract.Calendars.ACCOUNT_NAME} ASC"
    ) { cursor -> cursor.toCalendar(isWorkProfile = isWorkProfile) }

    private fun Cursor.toCalendar(isWorkProfile: Boolean): CalendarInfo {
        val id: Long = getLong(getColumnIndexOrThrow(CalendarContract.Calendars._ID))
        val color: Int = getInt(getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_COLOR))
        if (isWorkProfile) {
            return CalendarInfo(id = id, name = "", accountName = "", color = color, isWorkProfile = true)
        }
        return CalendarInfo(
            id = id,
            name = getString(getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)).orEmpty(),
            accountName = getString(getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_NAME)).orEmpty(),
            color = color,
            isWorkProfile = false
        )
    }

    private fun <T> query(
        uri: android.net.Uri,
        projection: Array<String>,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?,
        mapper: (Cursor) -> T
    ): List<T> {
        val cursor: Cursor = try {
            appContext.contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)
        } catch (exception: SecurityException) {
            return emptyList()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Timber.w(exception, "Calendar provider query failed for %s", uri)
            return emptyList()
        } ?: return emptyList()
        return try {
            cursor.use { results ->
                var hasLoggedRowFailure: Boolean = false
                buildList {
                    while (results.moveToNext()) {
                        try {
                            add(mapper(results))
                        } catch (exception: CancellationException) {
                            throw exception
                        } catch (exception: Exception) {
                            if (!hasLoggedRowFailure) {
                                Timber.w(exception, "Skipping unreadable calendar provider rows from %s", uri)
                                hasLoggedRowFailure = true
                            }
                        }
                    }
                }
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Timber.w(exception, "Calendar provider query failed for %s", uri)
            emptyList()
        }
    }

    private fun <T> contentObserverFlow(query: () -> List<T>): Flow<List<T>> = callbackFlow {
        val flowScope: ProducerScope<List<T>> = this
        val observer: ContentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                flowScope.launch(Dispatchers.IO) {
                    trySend(query())
                }
            }
        }
        val registeredUris: List<android.net.Uri> = registerObservers(observer = observer, uris = calendarUris())
        flowScope.launch(Dispatchers.IO) {
            trySend(query())
        }
        awaitClose {
            registeredUris.forEach { uri -> appContext.contentResolver.unregisterContentObserver(observer) }
        }
    }.distinctUntilChanged()

    private fun registerObservers(observer: ContentObserver, uris: List<android.net.Uri>): List<android.net.Uri> = buildList {
        val resolver: ContentResolver = appContext.contentResolver
        uris.forEach { uri ->
            try {
                resolver.registerContentObserver(uri, true, observer)
                add(uri)
            } catch (exception: SecurityException) {
                // The provider enforces cross-profile access to the enterprise calendar URI.
            } catch (exception: IllegalArgumentException) {
                // The provider may not expose this URI on every device.
            }
        }
    }
}

private val EVENT_PROJECTION: Array<String> = arrayOf(
    CalendarContract.Instances.EVENT_ID,
    CalendarContract.Instances.TITLE,
    CalendarContract.Instances.BEGIN,
    CalendarContract.Instances.END,
    CalendarContract.Instances.ALL_DAY,
    CalendarContract.Instances.CALENDAR_ID,
    CalendarContract.Instances.DISPLAY_COLOR
)

private val CALENDAR_PROJECTION: Array<String> = arrayOf(
    CalendarContract.Calendars._ID,
    CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
    CalendarContract.Calendars.ACCOUNT_NAME,
    CalendarContract.Calendars.CALENDAR_COLOR
)

/** The cross-profile provider rejects display names and account names, so work calendars are labelled in the UI. */
private val WORK_CALENDAR_PROJECTION: Array<String> = arrayOf(
    CalendarContract.Calendars._ID,
    CalendarContract.Calendars.CALENDAR_COLOR
)
