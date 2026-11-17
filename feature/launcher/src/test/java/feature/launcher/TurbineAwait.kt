package feature.launcher

import app.cash.turbine.TurbineTestContext

/** Awaits the first item matching [predicate], skipping any others. */
internal suspend fun <T> TurbineTestContext<T>.awaitFirstMatching(predicate: (T) -> Boolean): T {
    while (true) {
        val actualItem: T = awaitItem()
        if (predicate(actualItem)) {
            return actualItem
        }
    }
}
