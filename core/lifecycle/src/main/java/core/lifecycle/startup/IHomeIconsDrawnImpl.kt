package core.lifecycle.startup

import android.os.Trace
import core.ui.HOME_ICONS_DRAWN_TRACE_SECTION
import core.ui.IHomeIconsDrawn

internal const val HOME_ICONS_DRAWN_COOKIE: Int = 0

/**
 * Records one async trace section per process, from [start] at Application creation until
 * [report] after the frame that draws Home's Favorite icons. Both run on the main thread. An
 * async section is not nested in the framework's sections, so it spans the whole window instead
 * of ending with Application creation.
 */
class IHomeIconsDrawnImpl : IHomeIconsDrawn {
    private var isReported: Boolean = false

    override fun start() {
        Trace.beginAsyncSection(HOME_ICONS_DRAWN_TRACE_SECTION, HOME_ICONS_DRAWN_COOKIE)
    }

    override fun report() {
        if (isReported) {
            return
        }
        isReported = true
        Trace.endAsyncSection(HOME_ICONS_DRAWN_TRACE_SECTION, HOME_ICONS_DRAWN_COOKIE)
    }
}
