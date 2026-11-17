package core.ui

/** The trace section the startup benchmark reads the time to Home icons drawn from. */
const val HOME_ICONS_DRAWN_TRACE_SECTION: String = "HomeIconsDrawn"

/**
 * Measures the cold start until Home has drawn its visible Favorite icons, as one trace section
 * per process so the startup benchmark can report it beside TTID and TTFD. The window opens at
 * Application creation, a little after TTID and TTFD's zero point, so compare its value across
 * builds rather than against theirs.
 */
interface IHomeIconsDrawn {
    /** Opens the measured window once per process, at Application creation. */
    fun start()

    /** Closes the measured window; the first report wins and later calls do nothing. */
    fun report()
}
