package feature.launcher.widgets

import androidx.compose.runtime.Immutable
import core.data.media.MediaCommand
import core.data.media.MediaSessionSnapshot
import core.data.widgets.MediaPlayerPlacement

/** What Home shows for the built-in Media Player. */
sealed interface MediaPlayerUiState {
    data object Hidden : MediaPlayerUiState

    data class Visible(
        val session: MediaSessionSnapshot,
        val placement: MediaPlayerPlacement = MediaPlayerPlacement.BELOW_WIDGET_AREA,
        val showArtwork: Boolean = true
    ) : MediaPlayerUiState
}

/** The Media Player callbacks Home wires to the source. */
@Immutable
data class MediaPlayerActions(val onOpen: () -> Unit, val onLongPress: () -> Unit, val onCommand: (MediaCommand) -> Unit)
