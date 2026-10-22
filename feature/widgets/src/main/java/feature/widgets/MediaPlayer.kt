package feature.widgets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon as MaterialIcon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.SubcomposeAsyncImage
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Pause
import compose.icons.fontawesomeicons.solid.Play
import compose.icons.fontawesomeicons.solid.StepBackward
import compose.icons.fontawesomeicons.solid.StepForward
import core.data.icons.model.Icon
import core.data.media.MediaArtwork
import core.data.media.MediaCommand
import core.data.media.MediaSessionSnapshot
import core.data.media.PlaybackStatus

private val mediaPlayerShape: RoundedCornerShape = RoundedCornerShape(24.dp)
private val mediaArtworkShape: RoundedCornerShape = RoundedCornerShape(12.dp)
private val mediaPlayerMinHeight: Dp = 96.dp
private const val MEDIA_PLAYER_ANIMATION_MILLIS: Int = 200

internal const val MEDIA_PLAYER_KEY: String = "mediaPlayer"

/** Adds the Media Player below the Widget Area's places. */
fun LazyListScope.mediaPlayerItem(ui: WidgetAreaUi) {
    val spacingDp: Int = ui.state.widgetSpacingDp
    item(key = MEDIA_PLAYER_KEY) {
        MediaPlayerSlot(ui = ui, topSpacing = spacingDp.dp)
    }
}

/** Keeps the last session composed while the Media Player animates away. */
@Composable
fun MediaPlayerSlot(ui: WidgetAreaUi, topSpacing: Dp, modifier: Modifier = Modifier) {
    val mediaPlayer: MediaPlayerUiState = ui.mediaPlayer
    var retained: MediaPlayerUiState.Visible? by remember { mutableStateOf(null) }
    if (mediaPlayer is MediaPlayerUiState.Visible) {
        SideEffect { retained = mediaPlayer }
    }
    AnimatedVisibility(
        visible = mediaPlayer is MediaPlayerUiState.Visible,
        modifier = modifier.fillMaxWidth(),
        enter = fadeIn(animationSpec = tween(MEDIA_PLAYER_ANIMATION_MILLIS)) +
            expandVertically(animationSpec = tween(MEDIA_PLAYER_ANIMATION_MILLIS)),
        exit = fadeOut(animationSpec = tween(MEDIA_PLAYER_ANIMATION_MILLIS)) +
            shrinkVertically(animationSpec = tween(MEDIA_PLAYER_ANIMATION_MILLIS))
    ) {
        val shown: MediaPlayerUiState.Visible? = mediaPlayer as? MediaPlayerUiState.Visible ?: retained
        if (shown != null) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.height(topSpacing))
                MediaPlayer(
                    state = shown,
                    actions = MediaPlayerActions(
                        onOpen = ui.actions.onMediaPlayerOpen,
                        onLongPress = ui.actions.onMediaPlayerLongPress,
                        onCommand = ui.actions.onMediaCommand
                    )
                )
            }
        }
    }
}

/** The round-cornered Media Player card. */
@Composable
fun MediaPlayer(state: MediaPlayerUiState.Visible, actions: MediaPlayerActions, modifier: Modifier = Modifier) {
    val session: MediaSessionSnapshot = state.session
    val description: String = mediaPlayerAnnouncement(session = session)
    val openLabel: String = stringResource(R.string.media_open_player)
    val menuLabel: String = stringResource(R.string.media_player_menu)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = mediaPlayerMinHeight)
            .clip(mediaPlayerShape)
            .background(color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = SURFACE_ALPHA))
            .semantics {
                role = Role.Button
                onClick(label = openLabel) {
                    actions.onOpen()
                    true
                }
                onLongClick(label = menuLabel) {
                    actions.onLongPress()
                    true
                }
            }
            .pointerInput(session.key) {
                detectTapGestures(
                    onTap = { actions.onOpen() },
                    onLongPress = { actions.onLongPress() }
                )
            }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MediaArtworkImage(
            artwork = session.artwork,
            appPackageName = session.packageName,
            modifier = Modifier.size(64.dp).clip(mediaArtworkShape)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
                .semantics(mergeDescendants = true) { contentDescription = description }
        ) {
            Text(
                text = session.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium
            )
            session.artist?.let { artist ->
                Text(
                    text = artist,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = Icon.ApplicationIcon(packageName = session.packageName),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = session.appLabel,
                    modifier = Modifier.padding(start = 6.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        MediaControls(session = session, onCommand = actions.onCommand)
    }
}

@Composable
private fun MediaControls(session: MediaSessionSnapshot, onCommand: (MediaCommand) -> Unit) {
    if (MediaCommand.Previous in session.actions) {
        MediaControlButton(
            icon = FontAwesomeIcons.Solid.StepBackward,
            contentDescription = stringResource(R.string.media_previous),
            onClick = { onCommand(MediaCommand.Previous) }
        )
    }
    val playCommand: MediaCommand? = playPauseCommand(session = session)
    if (playCommand != null) {
        FilledTonalIconButton(
            onClick = { onCommand(playCommand) },
            modifier = Modifier.size(48.dp)
        ) {
            MaterialIcon(
                imageVector = if (playCommand == MediaCommand.Pause) {
                    FontAwesomeIcons.Solid.Pause
                } else {
                    FontAwesomeIcons.Solid.Play
                },
                contentDescription = stringResource(
                    if (playCommand == MediaCommand.Pause) R.string.media_pause else R.string.media_play
                )
            )
        }
    }
    if (MediaCommand.Next in session.actions) {
        MediaControlButton(
            icon = FontAwesomeIcons.Solid.StepForward,
            contentDescription = stringResource(R.string.media_next),
            onClick = { onCommand(MediaCommand.Next) }
        )
    }
}

@Composable
private fun MediaControlButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
        MaterialIcon(imageVector = icon, contentDescription = contentDescription)
    }
}

/** Loads artwork through Coil; falls back to the source app icon. */
@Composable
fun MediaArtworkImage(artwork: MediaArtwork?, appPackageName: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(color = MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (artwork == null) {
            AppIcon(packageName = appPackageName)
        } else {
            SubcomposeAsyncImage(
                model = artwork,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                error = { AppIcon(packageName = appPackageName) }
            )
        }
    }
}

@Composable
private fun AppIcon(packageName: String) {
    AsyncImage(
        model = Icon.ApplicationIcon(packageName = packageName),
        contentDescription = null,
        modifier = Modifier.size(40.dp)
    )
}

@Composable
private fun mediaPlayerAnnouncement(session: MediaSessionSnapshot): String = session.artist?.let { artist ->
    stringResource(R.string.media_player_announcement, session.title, artist, session.appLabel)
} ?: stringResource(R.string.media_player_announcement_no_artist, session.title, session.appLabel)

private fun playPauseCommand(session: MediaSessionSnapshot): MediaCommand? {
    val isPlaying: Boolean = session.state in PLAYING_STATES
    return when {
        isPlaying && MediaCommand.Pause in session.actions -> MediaCommand.Pause
        !isPlaying && MediaCommand.Play in session.actions -> MediaCommand.Play
        MediaCommand.Pause in session.actions -> MediaCommand.Pause
        MediaCommand.Play in session.actions -> MediaCommand.Play
        else -> null
    }
}

private val PLAYING_STATES: Set<PlaybackStatus> = setOf(
    PlaybackStatus.PLAYING,
    PlaybackStatus.BUFFERING,
    PlaybackStatus.CONNECTING,
    PlaybackStatus.FAST_FORWARDING,
    PlaybackStatus.REWINDING,
    PlaybackStatus.SKIPPING_TO_NEXT,
    PlaybackStatus.SKIPPING_TO_PREVIOUS,
    PlaybackStatus.SKIPPING_TO_QUEUE_ITEM
)

private const val SURFACE_ALPHA: Float = 0.85f
