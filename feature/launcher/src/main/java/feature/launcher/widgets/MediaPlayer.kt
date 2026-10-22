package feature.launcher.widgets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import feature.launcher.R

private val mediaArtworkSize: Dp = 80.dp
private val mediaArtworkShape: RoundedCornerShape = RoundedCornerShape(2.dp)
private val mediaArtworkBorderWidth: Dp = 1.dp
private const val MEDIA_ARTWORK_BORDER_ALPHA: Float = 0.5f
private val mediaArtworkSpacing: Dp = 16.dp
private val mediaArtistSourceSpacing: Dp = 8.dp
private val mediaSourceIconSize: Dp = 14.dp
private val mediaSourceSpacing: Dp = 4.dp
private val mediaControlsTopSpacing: Dp = 6.dp
private val mediaControlsSpacing: Dp = 4.dp
private val mediaControlSize: Dp = 44.dp
private val mediaControlIconSize: Dp = 22.dp
private val mediaPlayButtonSize: Dp = 40.dp
private val mediaPlayButtonBorderWidth: Dp = 1.dp
private val mediaPlayIconSize: Dp = 20.dp
private val mediaAppIconSize: Dp = 32.dp
private val mediaPlayerVerticalPadding: Dp = 4.dp
private const val MEDIA_PLAYER_ANIMATION_MILLIS: Int = 200

internal const val MEDIA_PLAYER_KEY: String = "mediaPlayer"

/** Adds the Media Player at the Widget Area position chosen in Settings. */
fun LazyListScope.mediaPlayerItem(ui: WidgetAreaUi, topSpacing: Dp, bottomSpacing: Dp) {
    item(key = MEDIA_PLAYER_KEY) {
        MediaPlayerSlot(
            ui = ui,
            topSpacing = topSpacing,
            bottomSpacing = bottomSpacing,
            modifier = Modifier.animateItem(placementSpec = tween(MEDIA_PLAYER_ANIMATION_MILLIS))
        )
    }
}

/** Keeps the last session composed while the Media Player animates away. */
@Composable
fun MediaPlayerSlot(ui: WidgetAreaUi, topSpacing: Dp, bottomSpacing: Dp, modifier: Modifier = Modifier) {
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
                Spacer(modifier = Modifier.height(bottomSpacing))
            }
        }
    }
}

/** The transparent Media Player row shown over the wallpaper. */
@Composable
fun MediaPlayer(state: MediaPlayerUiState.Visible, actions: MediaPlayerActions, modifier: Modifier = Modifier) {
    val session: MediaSessionSnapshot = state.session
    val description: String = mediaPlayerAnnouncement(session = session)
    val openLabel: String = stringResource(R.string.media_open_player)
    val menuLabel: String = stringResource(R.string.media_player_menu)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = mediaPlayerVerticalPadding)
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
            },
        verticalAlignment = Alignment.Top
    ) {
        if (state.showArtwork) {
            Box(
                modifier = Modifier
                    .size(mediaArtworkSize)
                    .clip(mediaArtworkShape)
            ) {
                MediaArtworkImage(
                    artwork = session.artwork,
                    appPackageName = session.packageName,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(
                            width = mediaArtworkBorderWidth,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = MEDIA_ARTWORK_BORDER_ALPHA),
                            shape = mediaArtworkShape
                        )
                )
            }
        } else {
            AsyncImage(
                model = Icon.ApplicationIcon(packageName = session.packageName),
                contentDescription = null,
                modifier = Modifier.size(mediaAppIconSize)
            )
        }
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .padding(start = mediaArtworkSpacing),
            horizontalAlignment = Alignment.Start
        ) {
            Column(
                modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = description }
            ) {
                Text(
                    text = session.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    session.artist?.let { artist ->
                        Text(
                            text = artist,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(mediaArtistSourceSpacing))
                    }
                    MediaSourceLabel(session = session)
                }
            }
            Spacer(modifier = Modifier.height(mediaControlsTopSpacing))
            MediaControls(
                session = session,
                onCommand = actions.onCommand,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
private fun MediaSourceLabel(session: MediaSessionSnapshot) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(
            model = Icon.ApplicationIcon(packageName = session.packageName),
            contentDescription = null,
            modifier = Modifier.size(mediaSourceIconSize)
        )
        Text(
            text = session.appLabel,
            modifier = Modifier.padding(start = mediaSourceSpacing),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MediaControls(session: MediaSessionSnapshot, onCommand: (MediaCommand) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(mediaControlsSpacing)
    ) {
        if (MediaCommand.Previous in session.actions) {
            MediaControlButton(
                icon = FontAwesomeIcons.Solid.StepBackward,
                contentDescription = stringResource(R.string.media_previous),
                onClick = { onCommand(MediaCommand.Previous) }
            )
        }
        val playCommand: MediaCommand? = playPauseCommand(session = session)
        if (playCommand != null) {
            PlayPauseButton(
                command = playCommand,
                onClick = { onCommand(playCommand) }
            )
        }
        if (MediaCommand.Next in session.actions) {
            MediaControlButton(
                icon = FontAwesomeIcons.Solid.StepForward,
                contentDescription = stringResource(R.string.media_next),
                onClick = { onCommand(MediaCommand.Next) }
            )
        }
    }
}

@Composable
private fun MediaControlButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(mediaControlSize)) {
        MaterialIcon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(mediaControlIconSize),
            tint = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun PlayPauseButton(command: MediaCommand, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(mediaPlayButtonSize)
            .clip(CircleShape)
            .border(
                width = mediaPlayButtonBorderWidth,
                color = MaterialTheme.colorScheme.onSurface,
                shape = CircleShape
            )
    ) {
        MaterialIcon(
            imageVector = if (command == MediaCommand.Pause) {
                FontAwesomeIcons.Solid.Pause
            } else {
                FontAwesomeIcons.Solid.Play
            },
            contentDescription = stringResource(
                if (command == MediaCommand.Pause) R.string.media_pause else R.string.media_play
            ),
            modifier = Modifier.size(mediaPlayIconSize),
            tint = MaterialTheme.colorScheme.onSurface
        )
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
