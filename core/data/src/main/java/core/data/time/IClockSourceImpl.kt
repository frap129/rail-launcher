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
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class IClockSourceImpl(private val context: Context) : IClockSource {
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
        ContextCompat.registerReceiver(context, timeReceiver, timeFilter, ContextCompat.RECEIVER_NOT_EXPORTED)

        val formatObserver: ContentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                emitReading()
            }
        }
        context.contentResolver.registerContentObserver(
            Settings.System.getUriFor(Settings.System.TIME_12_24),
            false,
            formatObserver
        )

        emitReading()

        awaitClose {
            minuteTicker.cancel()
            context.unregisterReceiver(timeReceiver)
            context.contentResolver.unregisterContentObserver(formatObserver)
        }
    }

    private fun readClock(): ClockReading = ClockReading(
        instant = Instant.now(),
        zoneId = ZoneId.systemDefault(),
        locale = Locale.getDefault(),
        isSystem24Hour = DateFormat.is24HourFormat(context)
    )

    private fun millisUntilNextMinute(): Long {
        val remainder: Long = System.currentTimeMillis() % MILLIS_PER_MINUTE
        return MILLIS_PER_MINUTE - remainder
    }
}
