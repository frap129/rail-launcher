package core.widgets

import core.data.widgets.IWidgetAreaRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val PAGE_SETTLE_DEBOUNCE_MS: Long = 300L

/** Debounces Stack page persistence so only the page settled last is stored. */
class WidgetPageSettler(private val scope: CoroutineScope, private val repository: IWidgetAreaRepository) {
    private val jobs: MutableMap<Long, Job> = mutableMapOf()

    /** Stores [page] for [placeId] once paging rests for the debounce window. */
    fun onPageSettled(placeId: Long, page: Int) {
        jobs.remove(placeId)?.cancel()
        jobs[placeId] = scope.launch {
            delay(PAGE_SETTLE_DEBOUNCE_MS)
            repository.setCurrentPage(placeId = placeId, page = page)
            jobs.remove(placeId)
        }
    }
}
