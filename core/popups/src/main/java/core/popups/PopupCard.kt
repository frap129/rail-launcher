package core.popups

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import core.data.shortcuts.ShortcutLaunchable
import core.ui.util.performLongPressHaptic

private val cardCornerRadius = 24.dp
private val cardShadowElevation = 6.dp
private val cardVerticalPadding = 8.dp
private val headerHeight = 56.dp
private val headerHorizontalPadding = 16.dp
private val headerIconSize = 32.dp
private val headerIconGap = 12.dp
private val shortcutRowHeight = 48.dp
private val shortcutHorizontalPadding = 16.dp
private val shortcutIconSize = 24.dp
private val shortcutIconGap = 16.dp
private val itemRowHeight = 48.dp
private val itemIconSize = 32.dp
private val itemIconGap = 12.dp
private val itemDividerInset = 16.dp
private val emptyStatePadding = 16.dp

/** The floating card that shows one App's header, shortcuts and added items. */
@Composable
fun PopupCard(
    content: PopupContent,
    onHeaderClick: () -> Unit,
    onShortcutClick: (ShortcutLaunchable) -> Unit,
    onItemClick: (PopupItem) -> Unit,
    onItemLongClick: (PopupItem) -> Unit,
    onRequestQuietModeOff: () -> Unit,
    onClose: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val closeLabel: String = stringResource(R.string.close_popup)
    val focusRequester: FocusRequester = remember { FocusRequester() }
    val hapticFeedback: HapticFeedback = LocalHapticFeedback.current
    val currentOnLongPress: () -> Unit by rememberUpdatedState(onLongPress)
    val currentOnItemLongClick: (PopupItem) -> Unit by rememberUpdatedState(onItemLongClick)
    val openMenu: () -> Unit = {
        hapticFeedback.performLongPressHaptic()
        currentOnLongPress()
    }
    val openItemMenu: (PopupItem) -> Unit = { item ->
        hapticFeedback.performLongPressHaptic()
        currentOnItemLongClick(item)
    }
    Surface(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {},
                    onLongPress = { openMenu() }
                )
            }
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(label = closeLabel) {
                        onClose()
                        true
                    }
                )
            },
        shape = RoundedCornerShape(cardCornerRadius),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = cardShadowElevation
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(vertical = cardVerticalPadding)
        ) {
            PopupHeader(
                header = content.header,
                focusRequester = focusRequester,
                onClick = onHeaderClick,
                onLongClick = openMenu
            )
            content.shortcuts.forEach { shortcut ->
                PopupShortcutRow(shortcut = shortcut, onClick = { onShortcutClick(shortcut) }, onLongClick = openMenu)
            }
            if (content.shortcuts.isNotEmpty() && content.items.isNotEmpty()) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = itemDividerInset),
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            }
            content.items.forEach { item ->
                PopupItemRow(
                    item = item,
                    onClick = { onItemClick(item) },
                    onLongClick = { openItemMenu(item) }
                )
            }
            when (content.emptyReason) {
                PopupEmptyReason.None -> Unit

                PopupEmptyReason.Empty -> PopupEmptyState(messageResId = R.string.popup_empty_hint)

                PopupEmptyReason.ShortcutsUnavailable -> PopupEmptyState(messageResId = R.string.set_rail_as_home_for_shortcuts)

                PopupEmptyReason.ProfilePaused -> PopupEmptyState(
                    messageResId = R.string.work_apps_paused,
                    onTurnOn = onRequestQuietModeOff
                )
            }
        }
    }
    LaunchedEffect(content.header.name) { focusRequester.requestFocus() }
}

@Composable
private fun PopupHeader(header: PopupHeader, focusRequester: FocusRequester, onClick: () -> Unit, onLongClick: () -> Unit) {
    val announcement: String = stringResource(R.string.popup_announcement, header.name)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(headerHeight)
            .focusRequester(focusRequester)
            .semantics {
                contentDescription = announcement
                heading()
            }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = headerHorizontalPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            modifier = Modifier.size(headerIconSize),
            model = header.icon,
            contentDescription = null
        )
        Spacer(Modifier.width(headerIconGap))
        Text(
            text = header.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PopupShortcutRow(shortcut: ShortcutLaunchable, onClick: () -> Unit, onLongClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(shortcutRowHeight)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = shortcutHorizontalPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            modifier = Modifier.size(shortcutIconSize),
            model = shortcut.icon,
            contentDescription = null
        )
        Spacer(Modifier.width(shortcutIconGap))
        PopupShortcutLabel(shortcut = shortcut)
    }
}

@Composable
internal fun PopupItemRow(item: PopupItem, onClick: () -> Unit, onLongClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(itemRowHeight)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = shortcutHorizontalPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            modifier = Modifier.size(itemIconSize),
            model = item.launchable.icon,
            contentDescription = null
        )
        Spacer(Modifier.width(itemIconGap))
        Text(
            text = item.launchable.name,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun PopupShortcutLabel(shortcut: ShortcutLaunchable) {
    var useLongLabel: Boolean by remember(shortcut.longLabel, shortcut.shortLabel) { mutableStateOf(true) }
    val label: String = if (useLongLabel) shortcut.longLabel else shortcut.shortLabel
    Text(
        text = label.ifBlank { shortcut.shortLabel },
        style = MaterialTheme.typography.bodyLarge,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { result ->
            if (useLongLabel && result.hasVisualOverflow && shortcut.shortLabel != shortcut.longLabel) {
                useLongLabel = false
            }
        }
    )
}

@Composable
internal fun PopupEmptyState(messageResId: Int, onTurnOn: (() -> Unit)? = null) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(emptyStatePadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(messageResId),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (onTurnOn != null) {
            TextButton(onClick = onTurnOn) {
                Text(stringResource(R.string.turn_on))
            }
        }
    }
}
