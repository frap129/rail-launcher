package feature.widgets

import androidx.compose.runtime.Immutable
import core.data.media.MediaCommand
import core.data.media.MediaSessionSnapshot

/** Where the Media Player sits relative to the Widget Area. */
enum class MediaPlayerPlacement {
    BelowWidgetArea,
    TopOfWidgetArea
}

/** What Home shows for the built-in Media Player. */
sealed interface MediaPlayerUiState {
    data object Hidden : MediaPlayerUiState

    data class Visible(val session: MediaSessionSnapshot, val placement: MediaPlayerPlacement = MediaPlayerPlacement.BelowWidgetArea) :
        MediaPlayerUiState
}

/** The Media Player callbacks Home wires to the source. */
@Immutable
data class MediaPlayerActions(val onOpen: () -> Unit, val onLongPress: () -> Unit, val onCommand: (MediaCommand) -> Unit)
