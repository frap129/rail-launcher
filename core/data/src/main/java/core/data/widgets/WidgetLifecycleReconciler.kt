package core.data.widgets

import core.data.room.dao.WidgetAreaDao
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Keeps Rail's App Widget database in step with the ids and providers the system knows. */
class WidgetLifecycleReconciler(private val dao: WidgetAreaDao, private val host: IAppWidgetHostSource) {
    private val mutex: Mutex = Mutex()
    private val startupReconciled: MutableStateFlow<Boolean> = MutableStateFlow(false)
    private val removed: MutableSharedFlow<AppWidgetId> = MutableSharedFlow(
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /** True once [reconcileAtStartup] has completed, so App Widgets can be rendered. */
    val isStartupReconciled: StateFlow<Boolean> = startupReconciled.asStateFlow()

    /** App Widget ids whose database row was removed by reconciliation. */
    val removedIds: Flow<AppWidgetId> = removed.asSharedFlow()

    /**
     * Frees system ids Rail has no row for and drops rows whose system id is gone.
     * Ids in [pendingIds] belong to an add flow that is still completing and are kept.
     */
    suspend fun reconcileAtStartup(pendingIds: Set<AppWidgetId>) {
        mutex.withLock {
            if (startupReconciled.value) {
                return
            }
            val stored: List<WidgetRecord> = dao.getAppWidgets()
            val storedIds: Set<Int> = stored.mapNotNull { widget -> widget.appWidgetId }.toSet()
            val protectedIds: Set<Int> = pendingIds.mapTo(mutableSetOf()) { id -> id.value }
            val systemIds: Set<Int> = host.boundIds().mapTo(mutableSetOf()) { id -> id.value }

            (systemIds - storedIds - protectedIds).forEach { id -> host.deleteId(AppWidgetId(id)) }

            val dead: List<WidgetRecord> = stored.filter { widget ->
                val appWidgetId: Int = widget.appWidgetId ?: return@filter false
                appWidgetId !in systemIds && appWidgetId !in protectedIds
            }
            if (dead.isNotEmpty()) {
                dao.deleteByAppWidgetIds(dead.mapNotNull { widget -> widget.appWidgetId })
                dead.forEach { widget ->
                    widget.appWidgetId?.let { id -> removed.tryEmit(AppWidgetId(id)) }
                }
            }
            startupReconciled.value = true
        }
    }

    /** Removes rows whose provider package is no longer installed for its profile. */
    suspend fun removeGoneProviders() {
        mutex.withLock {
            val gone: List<WidgetRecord> = dao.getAppWidgets().filter { widget ->
                widget.availability(host = host) == WidgetAvailability.Gone
            }
            if (gone.isEmpty()) {
                return
            }
            dao.deleteByAppWidgetIds(gone.mapNotNull { widget -> widget.appWidgetId })
            gone.forEach { widget ->
                widget.appWidgetId?.let { id ->
                    host.deleteId(AppWidgetId(id))
                    removed.tryEmit(AppWidgetId(id))
                }
            }
        }
    }
}

/** Resolves a stored App Widget's availability, defaulting malformed rows to Available. */
internal fun WidgetRecord.availability(host: IAppWidgetHostSource): WidgetAvailability {
    val appWidgetId: Int = appWidgetId ?: return WidgetAvailability.Available
    val provider: WidgetProvider = provider?.let(WidgetProvider::fromKey) ?: return WidgetAvailability.Available
    val profileSerial: Long = profileSerial ?: return WidgetAvailability.Available
    return host.availability(id = AppWidgetId(appWidgetId), provider = provider, profileSerial = profileSerial)
}
