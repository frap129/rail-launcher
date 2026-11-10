package core.ui.model.data

import java.io.Serializable

/**
 * A signal the launcher activity hands to the launcher UI.
 */
sealed interface LauncherSignal : Serializable {
    /**
     * A HOME intent delivered to the launcher.
     *
     * @property wasResumed whether the launcher activity was already resumed when the intent arrived,
     * which is only true for a Home press on Home and never for one that returns to Rail from another app.
     */
    data class Home(val wasResumed: Boolean) : LauncherSignal

    /** The Search entry asked to open Search. */
    data object OpenSearch : LauncherSignal

    /** A printable character was typed on the launcher with a hardware keyboard. */
    data class TypedCharacter(val character: String) : LauncherSignal
}
