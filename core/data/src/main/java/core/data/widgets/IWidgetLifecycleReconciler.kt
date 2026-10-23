package core.data.widgets

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Keeps Rail's App Widget database in step with the ids and providers the system knows. */
interface IWidgetLifecycleReconciler {
    /** True once [reconcileAtStartup] has completed, so App Widgets can be rendered. */
    val isStartupReconciled: StateFlow<Boolean>

    /** App Widget ids whose database row was removed by reconciliation. */
    val removedIds: Flow<AppWidgetId>

    /**
     * Frees system ids Rail has no row for and drops rows whose system id is gone.
     * Ids in [pendingIds] belong to an add flow that is still completing and are kept.
     */
    suspend fun reconcileAtStartup(pendingIds: Set<AppWidgetId>)

    /** Removes rows whose provider package is no longer installed for its profile. */
    suspend fun removeGoneProviders()
}
