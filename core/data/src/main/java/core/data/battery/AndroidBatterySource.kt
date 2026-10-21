package core.data.battery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.shareIn

private const val SUBSCRIPTION_TIMEOUT_MS: Long = 5_000L

class AndroidBatterySource(context: Context, scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)) :
    IBatterySource {
    private val appContext: Context = context.applicationContext

    override val status: Flow<BatteryStatus> = callbackFlow {
        val receiver: BroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                intent?.toBatteryStatus()?.let { status -> trySend(status) }
            }
        }
        ContextCompat.registerReceiver(
            appContext,
            receiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        awaitClose { appContext.unregisterReceiver(receiver) }
    }
        .distinctUntilChanged()
        .shareIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
            replay = 1
        )
}

internal fun Intent.toBatteryStatus(): BatteryStatus? {
    val level: Int = getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale: Int = getIntExtra(BatteryManager.EXTRA_SCALE, -1)
    if (level < 0 || scale <= 0) {
        return null
    }
    val status: Int = getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
    val plugged: Int = getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
    val isCharging: Boolean = status == BatteryManager.BATTERY_STATUS_CHARGING ||
        status == BatteryManager.BATTERY_STATUS_FULL ||
        plugged != 0
    return BatteryStatus(percent = level * 100 / scale, isCharging = isCharging)
}
