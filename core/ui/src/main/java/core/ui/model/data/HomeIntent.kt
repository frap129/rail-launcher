package core.ui.model.data

/**
 * A HOME intent delivered to the launcher.
 *
 * @property wasResumed whether the launcher activity was already resumed when the intent arrived,
 * which is only true for a Home press on Home and never for one that returns to Rail from another app.
 */
data class HomeIntent(val wasResumed: Boolean)
