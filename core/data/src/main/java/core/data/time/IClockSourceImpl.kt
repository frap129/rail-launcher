package core.data.time

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.format.DateFormat
import androidx.core.content.ContextCompat
import core.util.MILLIS_PER_MINUTE
import core.util.WHILE_SUBSCRIBED_TIMEOUT_MS
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class IClockSourceImpl(
    context: Context,
    scope: CoroutineScope,
    private val currentInstant: () -> Instant = Instant::now,
    private val currentZone: () -> ZoneId = ZoneId::systemDefault,
    private val currentLocale: () -> Locale = Locale::getDefault
) : IClockSource {
    private val appContext: Context = context.applicationContext

    override val readings: Flow<ClockReading> = channelFlow {
        fun emitReading() {
            trySend(readClock())
        }

        val minuteTicker: Job = launch {
            while (isActive) {
                delay(millisUntilNextMinute())
                emitReading()
            }
        }

        val timeReceiver: BroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                emitReading()
            }
        }
        val timeFilter: IntentFilter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_LOCALE_CHANGED)
        }
        ContextCompat.registerReceiver(appContext, timeReceiver, timeFilter, ContextCompat.RECEIVER_NOT_EXPORTED)

        val formatObserver: ContentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                emitReading()
            }
        }
        appContext.contentResolver.registerContentObserver(
            Settings.System.getUriFor(Settings.System.TIME_12_24),
            false,
            formatObserver
        )

        awaitClose {
            minuteTicker.cancel()
            appContext.unregisterReceiver(timeReceiver)
            appContext.contentResolver.unregisterContentObserver(formatObserver)
        }
    }
        .shareIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(WHILE_SUBSCRIBED_TIMEOUT_MS)
        )
        .onStart { emit(readClock()) }

    override fun now(): Instant = currentInstant()

    private fun readClock(): ClockReading = ClockReading(
        instant = now(),
        zoneId = currentZone(),
        locale = currentLocale(),
        isSystem24Hour = DateFormat.is24HourFormat(appContext)
    )

    private fun millisUntilNextMinute(): Long {
        val remainder: Long = now().toEpochMilli() % MILLIS_PER_MINUTE
        return MILLIS_PER_MINUTE - remainder
    }
}
