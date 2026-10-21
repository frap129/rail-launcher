package core.data.widgets

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The system ids allocated by an App Widget add flow that has not completed yet. */
interface IAddFlowPendingIds {
    /** Every id reserved by an add flow that has not finished. */
    val ids: StateFlow<Set<AppWidgetId>>

    /** Replaces the reserved ids. */
    fun set(ids: Set<AppWidgetId>)
}

/**
 * Persists the pending ids in SharedPreferences so startup reconciliation does not
 * free an id whose configuration activity is returning after process recreation.
 */
class IAddFlowPendingIdsImpl(context: Context) : IAddFlowPendingIds {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val pending: MutableStateFlow<Set<AppWidgetId>> = MutableStateFlow(readIds())

    override val ids: StateFlow<Set<AppWidgetId>> = pending.asStateFlow()

    override fun set(ids: Set<AppWidgetId>) {
        pending.value = ids
        preferences.edit()
            .putStringSet(KEY_IDS, ids.mapTo(mutableSetOf()) { id -> id.value.toString() })
            .commit()
    }

    private fun readIds(): Set<AppWidgetId> = preferences.getStringSet(KEY_IDS, emptySet())
        .orEmpty()
        .mapNotNull { value -> value.toIntOrNull() }
        .mapTo(mutableSetOf(), ::AppWidgetId)

    private companion object {
        const val PREFERENCES_NAME: String = "widget_add_flow"
        const val KEY_IDS: String = "pending_app_widget_ids"
    }
}
