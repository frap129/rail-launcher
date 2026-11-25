package core.ui.model.data

import java.io.Serializable

/**
 * A one-shot request the launcher UI acts on: a HOME intent, a Keyboard shortcut, the Search entry
 * or a Typed character the launcher activity hands it, or the agenda the launcher UI opens on its
 * own.
 */
sealed interface LauncherSignal : Serializable {
    /** Whether handling this signal closes an open Pop-up card. */
    val closesPopupCard: Boolean

    /**
     * A HOME intent delivered to the launcher.
     *
     * @property shouldShowHome whether the HOME intent presses the shown launcher rather than returning
     * from another app, so only that press resets the launcher to Home.
     * @property wasFocused whether the launcher had window focus at HOME intent arrival or just before
     * a delivery pause, which the App narrows to a Home press on Home so only that press can open Search.
     */
    data class Home(val shouldShowHome: Boolean, val wasFocused: Boolean) : LauncherSignal {
        override val closesPopupCard: Boolean = true
    }

    /** A Search entry or a Keyboard shortcut asked to open Search. */
    data object OpenSearch : LauncherSignal {
        override val closesPopupCard: Boolean = true
    }

    /** A Header Tap action or a Keyboard shortcut asked to open the agenda. */
    data object OpenAgenda : LauncherSignal {
        override val closesPopupCard: Boolean = true
    }

    /** A Keyboard shortcut asked to open Rail Settings. */
    data object OpenSettings : LauncherSignal {
        override val closesPopupCard: Boolean = true
    }

    /** A Keyboard shortcut asked to switch between Home and the App List. */
    data object ToggleAppList : LauncherSignal {
        override val closesPopupCard: Boolean = false
    }

    /** A Keyboard shortcut asked to open the Keyboard shortcuts help sheet. */
    data object OpenKeyboardShortcutsHelp : LauncherSignal {
        override val closesPopupCard: Boolean = true
    }

    /**
     * A Keyboard shortcut asked to open a row in the shown Home or App List order.
     *
     * @property index the zero-based row index.
     */
    data class OpenIndex(val index: Int) : LauncherSignal {
        override val closesPopupCard: Boolean = false
    }

    /** A printable character was typed on the launcher with a hardware keyboard. */
    data class TypedCharacter(val character: String) : LauncherSignal {
        override val closesPopupCard: Boolean = false
    }
}

/** Keeps Home navigation on every destination while limiting Search to a focused HOME intent already on Home. */
internal fun LauncherSignal.Home.narrowSearchToStartDestination(isStartDestination: Boolean): LauncherSignal.Home =
    copy(wasFocused = wasFocused && isStartDestination)
