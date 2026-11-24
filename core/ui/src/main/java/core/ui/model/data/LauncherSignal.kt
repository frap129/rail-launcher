package core.ui.model.data

import java.io.Serializable

/**
 * A one-shot request the launcher UI acts on: a HOME intent, a Keyboard shortcut, the Search entry
 * or a Typed character the launcher activity hands it, or the agenda the launcher UI opens on its
 * own.
 */
sealed interface LauncherSignal : Serializable {
    /**
     * A HOME intent delivered to the launcher.
     *
     * @property shouldShowHome whether the HOME intent presses the shown launcher rather than returning
     * from another app, so only that press resets the launcher to Home.
     * @property wasFocused whether the launcher had window focus at HOME intent arrival or just before
     * a delivery pause, which the App narrows to a Home press on Home so only that press can open Search.
     */
    data class Home(val shouldShowHome: Boolean, val wasFocused: Boolean) : LauncherSignal

    /** A Search entry or a Keyboard shortcut asked to open Search. */
    data object OpenSearch : LauncherSignal

    /** A Header Tap action or a Keyboard shortcut asked to open the agenda. */
    data object OpenAgenda : LauncherSignal

    /** A Keyboard shortcut asked to open Rail Settings. */
    data object OpenSettings : LauncherSignal

    /** A printable character was typed on the launcher with a hardware keyboard. */
    data class TypedCharacter(val character: String) : LauncherSignal
}

/** Keeps Home navigation on every destination while limiting Search to a focused HOME intent already on Home. */
internal fun LauncherSignal.Home.narrowSearchToStartDestination(isStartDestination: Boolean): LauncherSignal.Home =
    copy(wasFocused = wasFocused && isStartDestination)
