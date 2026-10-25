package core.popups

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon as MaterialIcon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.ArrowUp
import compose.icons.fontawesomeicons.solid.ChevronDown
import core.data.icons.model.Icon
import core.data.notifications.NotificationAction
import core.data.notifications.NotificationSnapshot
import core.data.popups.PopupItem
import core.data.shortcuts.ShortcutLaunchable
import core.ui.R as CoreUiR
import core.ui.composables.SheetDragHandle
import core.ui.layout.minTouchTargetSize
import core.ui.util.consumeTouches
import core.ui.util.performLongPressHaptic
import core.widgets.AppWidgetContent
import core.widgets.ResizeOverlay
import core.widgets.ResizeState
import core.widgets.WidgetStackPager

private val cardCornerRadius = 24.dp
private val cardShadowElevation = 6.dp
private val cardVerticalPadding = 8.dp
private val headerHeight = 56.dp
private val headerHorizontalPadding = 16.dp
private val headerIconSize = 32.dp
private val headerIconGap = 12.dp
private val notificationIconSize = 24.dp
private val notificationIconGap = 12.dp
private val notificationHorizontalPadding = 16.dp
private val notificationVerticalPadding = 8.dp
private val notificationMinHeight = 64.dp
private val notificationChevronSize = 16.dp
private val notificationMemberIndent = 16.dp
private val notificationExpandedTopPadding = 8.dp
private const val NOTIFICATION_BODY_LINES: Int = 1
private const val NOTIFICATION_EXPANDED_BODY_LINES: Int = 8
private const val NOTIFICATION_EXPANDED_CHEVRON_DEGREES: Float = 180f
private const val MAX_VISIBLE_NOTIFICATION_ACTIONS: Int = 3
private const val NOTIFICATION_REPLY_MAX_LINES: Int = 3
private val shortcutRowHeight = 48.dp
private val shortcutHorizontalPadding = 16.dp
private val shortcutIconSize = 24.dp
private val shortcutIconGap = 16.dp
private val itemRowHeight = 48.dp
private val itemIconSize = 32.dp
private val itemIconGap = 12.dp
private val itemDividerInset = 16.dp
private val emptyStatePadding = 16.dp
private val addAppsButtonTopPadding = 8.dp
internal val popupWidgetHorizontalPadding: Dp = 8.dp

@Immutable
private data class NotificationRowsUi(
    val notifications: List<NotificationSnapshot>,
    val appName: String,
    val icon: Icon,
    val isIndented: Boolean
)

@Immutable
private data class NotificationRowUi(
    val snapshot: NotificationSnapshot,
    val appName: String,
    val icon: Icon,
    val nowMillis: Long,
    val isExpanded: Boolean,
    val isReplySent: Boolean,
    val isIndented: Boolean,
    val actions: PopupNotificationActions
)

/** The floating card that shows one App's header, shortcuts and added items. */
@Composable
fun PopupCard(
    content: PopupContent,
    widget: PopupWidgetState?,
    nowMillis: Long,
    resize: ResizeState?,
    expandedNotificationKeys: Set<String>,
    sentNotificationKeys: Set<String>,
    notificationActions: PopupNotificationActions,
    onHeaderClick: (() -> Unit)?,
    onShortcutClick: (ShortcutLaunchable) -> Unit,
    onItemClick: (PopupItem) -> Unit,
    onItemLongClick: (PopupItem) -> Unit,
    onAddItems: () -> Unit,
    onRequestQuietModeOff: () -> Unit,
    onWidgetLongPress: (Long) -> Unit,
    onWidgetUnpause: (Long) -> Unit,
    onWidgetPageChanged: (Int) -> Unit,
    onResizeDrag: (Float) -> Unit,
    onResizeReset: () -> Unit,
    onResizeDone: () -> Unit,
    onResizeTap: () -> Unit,
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
    val isResizing: Boolean = resize != null
    Surface(
        modifier = modifier
            .pointerInput(isResizing) {
                detectTapGestures(
                    onTap = { if (isResizing) onResizeTap() },
                    onLongPress = { if (!isResizing) openMenu() }
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
            widget?.let { cardWidget ->
                PopupWidgetSection(
                    widget = cardWidget,
                    resize = resize,
                    onLongPress = onWidgetLongPress,
                    onUnpause = onWidgetUnpause,
                    onPageChanged = onWidgetPageChanged,
                    onDrag = onResizeDrag,
                    onReset = onResizeReset,
                    onResizeTap = onResizeTap
                )
            }
            if (isResizing) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = popupWidgetHorizontalPadding),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onResizeDone) {
                        Text(text = stringResource(CoreUiR.string.done))
                    }
                }
            }
            Box(
                modifier = if (isResizing) {
                    Modifier.fillMaxWidth().consumeTouches(onGestureEnd = onResizeTap)
                } else {
                    Modifier.fillMaxWidth()
                }
            ) {
                Column {
                    PopupHeader(
                        header = content.header,
                        focusRequester = focusRequester,
                        onClick = if (isResizing) null else onHeaderClick,
                        onLongClick = openMenu
                    )
                    NotificationRows(
                        ui = NotificationRowsUi(
                            notifications = content.ownerNotifications,
                            appName = content.header.name,
                            icon = content.header.icon,
                            isIndented = false
                        ),
                        nowMillis = nowMillis,
                        expandedNotificationKeys = expandedNotificationKeys,
                        sentNotificationKeys = sentNotificationKeys,
                        actions = notificationActions
                    )
                    if (content.ownerNotifications.isNotEmpty() && (content.shortcuts.isNotEmpty() || content.items.isNotEmpty())) {
                        PopupDivider()
                    }
                    content.shortcuts.forEach { shortcut ->
                        PopupShortcutRow(shortcut = shortcut, onClick = { onShortcutClick(shortcut) }, onLongClick = openMenu)
                    }
                    if (content.shortcuts.isNotEmpty() && content.items.isNotEmpty()) {
                        PopupDivider()
                    }
                    content.items.forEach { item ->
                        PopupItemRow(
                            item = item,
                            onClick = { onItemClick(item) },
                            onLongClick = { openItemMenu(item) }
                        )
                        NotificationRows(
                            ui = NotificationRowsUi(
                                notifications = content.itemNotifications[item.launchable.key].orEmpty(),
                                appName = item.launchable.name,
                                icon = item.launchable.icon,
                                isIndented = true
                            ),
                            nowMillis = nowMillis,
                            expandedNotificationKeys = expandedNotificationKeys,
                            sentNotificationKeys = sentNotificationKeys,
                            actions = notificationActions
                        )
                    }
                    val emptyReason: PopupEmptyReason = when (content.emptyReason) {
                        PopupEmptyReason.Empty,
                        PopupEmptyReason.FolderEmpty -> if (widget == null) content.emptyReason else PopupEmptyReason.None

                        else -> content.emptyReason
                    }
                    when (emptyReason) {
                        PopupEmptyReason.None -> Unit

                        PopupEmptyReason.Empty -> PopupEmptyState(messageResId = R.string.popup_empty_hint)

                        PopupEmptyReason.FolderEmpty -> PopupEmptyState(
                            messageResId = R.string.folder_empty,
                            onAddApps = onAddItems
                        )

                        PopupEmptyReason.ShortcutsUnavailable ->
                            PopupEmptyState(messageResId = R.string.set_rail_as_home_for_shortcuts)

                        PopupEmptyReason.ProfilePaused -> PopupEmptyState(
                            messageResId = R.string.work_apps_paused,
                            onTurnOn = onRequestQuietModeOff
                        )
                    }
                }
            }
        }
    }
    LaunchedEffect(content.header.name) { focusRequester.requestFocus() }
}

@Composable
private fun PopupWidgetSection(
    widget: PopupWidgetState,
    resize: ResizeState?,
    onLongPress: (Long) -> Unit,
    onUnpause: (Long) -> Unit,
    onPageChanged: (Int) -> Unit,
    onDrag: (Float) -> Unit,
    onReset: () -> Unit,
    onResizeTap: () -> Unit
) {
    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = popupWidgetHorizontalPadding)) {
        val heightDp: Int = resize?.draftHeightDp ?: widget.heightDp
        val isResizing: Boolean = resize != null
        val content: @Composable () -> Unit = {
            WidgetStackPager(
                pageCount = widget.pages.size,
                currentPage = widget.currentPage,
                heightDp = heightDp,
                indicatorVisible = widget.indicatorVisible,
                pageKey = { page -> widget.pages[page].widgetId },
                onPageSettled = onPageChanged,
                userScrollEnabled = !isResizing,
                modifier = if (isResizing) Modifier.consumeTouches(onGestureEnd = onResizeTap) else Modifier
            ) { page, pageHeightDp ->
                AppWidgetContent(
                    widget = widget.pages[page].model,
                    heightDp = pageHeightDp,
                    menuEnabled = !isResizing,
                    isResizing = isResizing,
                    onLongPress = onLongPress,
                    onUnpause = onUnpause
                )
            }
        }
        if (resize == null) {
            content()
        } else {
            ResizeOverlay(
                state = resize,
                onDrag = onDrag,
                onReset = onReset
            ) {
                content()
            }
        }
    }
}

@Composable
private fun PopupHeader(header: PopupHeader, focusRequester: FocusRequester, onClick: (() -> Unit)?, onLongClick: () -> Unit) {
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
            .then(
                if (onClick == null) {
                    Modifier
                } else {
                    Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
                }
            )
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
private fun PopupDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = itemDividerInset),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

@Composable
private fun NotificationRows(
    ui: NotificationRowsUi,
    nowMillis: Long,
    expandedNotificationKeys: Set<String>,
    sentNotificationKeys: Set<String>,
    actions: PopupNotificationActions
) {
    ui.notifications.forEach { snapshot ->
        key(snapshot.key) {
            PopupNotificationRow(
                row = NotificationRowUi(
                    snapshot = snapshot,
                    appName = ui.appName,
                    icon = ui.icon,
                    nowMillis = nowMillis,
                    isExpanded = snapshot.key in expandedNotificationKeys,
                    isReplySent = snapshot.key in sentNotificationKeys,
                    isIndented = ui.isIndented,
                    actions = actions
                )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PopupNotificationRow(row: NotificationRowUi) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value != SwipeToDismissBoxValue.Settled) {
                row.actions.onDismiss(row.snapshot)
            }
            true
        }
    )
    val content: @Composable () -> Unit = {
        NotificationRowContent(row = row)
    }
    if (row.snapshot.isClearable) {
        SwipeToDismissBox(
            state = dismissState,
            backgroundContent = {},
            enableDismissFromStartToEnd = true,
            enableDismissFromEndToStart = true
        ) {
            content()
        }
    } else {
        content()
    }
}

@Composable
private fun NotificationRowContent(row: NotificationRowUi) {
    val snapshot = row.snapshot
    val expandLabel: String = stringResource(R.string.notification_expand)
    val collapseLabel: String = stringResource(R.string.notification_collapse)
    val dismissLabel: String = stringResource(R.string.notification_dismiss)
    val openLabel: String = stringResource(R.string.notification_open)
    val announcement: String = stringResource(R.string.notification_announcement, row.appName, snapshot.title)
    val actionLayout: NotificationActionLayout = notificationActionLayout(actions = snapshot.actions)
    val toggleLabel: String = if (row.isExpanded) collapseLabel else expandLabel
    val clickLabel: String = if (row.isExpanded) collapseLabel else openLabel
    val onRowClick: () -> Unit = if (row.isExpanded) {
        { row.actions.onToggleExpanded(snapshot) }
    } else {
        { row.actions.onOpen(snapshot) }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .padding(start = if (row.isIndented) notificationMemberIndent else 0.dp)
            .padding(horizontal = notificationHorizontalPadding, vertical = notificationVerticalPadding)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = notificationMinHeight)
                .clearAndSetSemantics {
                    contentDescription = announcement
                    onClick(label = clickLabel) {
                        onRowClick()
                        true
                    }
                    customActions = buildList {
                        if (snapshot.isExpandable) {
                            add(
                                CustomAccessibilityAction(label = toggleLabel) {
                                    row.actions.onToggleExpanded(snapshot)
                                    true
                                }
                            )
                        }
                        if (snapshot.isClearable) {
                            add(
                                CustomAccessibilityAction(label = dismissLabel) {
                                    row.actions.onDismiss(snapshot)
                                    true
                                }
                            )
                        }
                    }
                }
                .clickable(onClick = onRowClick),
            verticalAlignment = Alignment.Top
        ) {
            AsyncImage(
                modifier = Modifier.size(notificationIconSize),
                model = row.icon,
                contentDescription = null
            )
            Spacer(Modifier.width(notificationIconGap))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(
                        R.string.notification_app_and_age,
                        row.appName,
                        notificationAgeLabel(postTime = snapshot.postTime, nowMillis = row.nowMillis)
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (snapshot.title.isNotBlank()) {
                    Text(
                        text = snapshot.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (!row.isExpanded && snapshot.body.isNotBlank()) {
                    Text(
                        text = snapshot.body,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = NOTIFICATION_BODY_LINES,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (snapshot.isExpandable) {
                IconButton(modifier = Modifier.size(minTouchTargetSize), onClick = { row.actions.onToggleExpanded(snapshot) }) {
                    MaterialIcon(
                        modifier = Modifier
                            .size(notificationChevronSize)
                            .rotate(if (row.isExpanded) NOTIFICATION_EXPANDED_CHEVRON_DEGREES else 0f),
                        imageVector = FontAwesomeIcons.Solid.ChevronDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        if (row.isExpanded) {
            NotificationExpandedContent(row = row, actionLayout = actionLayout)
        }
    }
}

@Composable
private fun NotificationExpandedContent(row: NotificationRowUi, actionLayout: NotificationActionLayout) {
    val snapshot = row.snapshot
    val visibleButtons: List<IndexedValue<NotificationAction>> = actionLayout.buttons.take(MAX_VISIBLE_NOTIFICATION_ACTIONS)
    val overflowButtons: List<IndexedValue<NotificationAction>> = actionLayout.buttons.drop(MAX_VISIBLE_NOTIFICATION_ACTIONS)
    var showActionSheet: Boolean by remember(snapshot.key) { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        if (snapshot.expandedBody.isNotBlank()) {
            Text(
                modifier = Modifier.padding(top = notificationExpandedTopPadding),
                text = snapshot.expandedBody,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = NOTIFICATION_EXPANDED_BODY_LINES,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (visibleButtons.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.Start) {
                visibleButtons.forEach { indexed ->
                    TextButton(onClick = { row.actions.onAction(snapshot, indexed.index) }) {
                        Text(text = indexed.value.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (overflowButtons.isNotEmpty()) {
                    TextButton(onClick = { showActionSheet = true }) {
                        Text(text = stringResource(R.string.notification_more_actions))
                    }
                }
            }
        }
        if (actionLayout.replyIndex >= 0) {
            if (row.isReplySent) {
                Text(
                    modifier = Modifier.padding(vertical = notificationVerticalPadding),
                    text = stringResource(R.string.notification_sent),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                NotificationReplyField(onSend = { reply -> row.actions.onReply(snapshot, actionLayout.replyIndex, reply) })
            }
        }
    }
    if (showActionSheet) {
        NotificationActionsSheet(
            title = snapshot.title,
            actions = overflowButtons,
            onAction = { index ->
                showActionSheet = false
                row.actions.onAction(snapshot, index)
            },
            onDismiss = { showActionSheet = false }
        )
    }
}

@Composable
private fun NotificationReplyField(onSend: (String) -> Unit) {
    var replyText: String by rememberSaveable { mutableStateOf("") }
    val sendLabel: String = stringResource(R.string.notification_send)
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            modifier = Modifier.weight(1f),
            value = replyText,
            onValueChange = { value -> replyText = value },
            placeholder = { Text(text = stringResource(R.string.notification_reply_hint)) },
            maxLines = NOTIFICATION_REPLY_MAX_LINES
        )
        IconButton(
            onClick = {
                if (replyText.isNotBlank()) {
                    onSend(replyText)
                    replyText = ""
                }
            },
            enabled = replyText.isNotBlank()
        ) {
            MaterialIcon(
                imageVector = FontAwesomeIcons.Solid.ArrowUp,
                contentDescription = sendLabel
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationActionsSheet(
    title: String,
    actions: List<IndexedValue<NotificationAction>>,
    onAction: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        dragHandle = { SheetDragHandle() },
        onDismissRequest = onDismiss
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            PopupSheetTitleRow(title = title)
            actions.forEach { indexed ->
                TextButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = notificationHorizontalPadding),
                    onClick = { onAction(indexed.index) }
                ) {
                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        text = indexed.value.label,
                        textAlign = TextAlign.Start
                    )
                }
            }
        }
    }
}

@Composable
private fun notificationAgeLabel(postTime: Long, nowMillis: Long): String {
    val age: NotificationAge = notificationAge(postTime = postTime, now = nowMillis)
    return when (age) {
        NotificationAge.Now -> stringResource(R.string.notification_age_now)
        is NotificationAge.Minutes -> stringResource(R.string.notification_age_minutes, age.value)
        is NotificationAge.Hours -> stringResource(R.string.notification_age_hours, age.value)
        is NotificationAge.Days -> stringResource(R.string.notification_age_days, age.value)
        is NotificationAge.Weeks -> stringResource(R.string.notification_age_weeks, age.value)
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
    val shortcut: ShortcutLaunchable? = (item as? PopupItem.Shortcut)?.launchable
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(itemRowHeight)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = shortcutHorizontalPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            modifier = Modifier.size(if (shortcut != null) shortcutIconSize else itemIconSize),
            model = item.launchable.icon,
            contentDescription = null
        )
        Spacer(Modifier.width(if (shortcut != null) shortcutIconGap else itemIconGap))
        if (shortcut != null) {
            PopupShortcutLabel(shortcut = shortcut)
        } else {
            Text(
                text = item.launchable.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun PopupShortcutLabel(shortcut: ShortcutLaunchable, modifier: Modifier = Modifier) {
    var useLongLabel: Boolean by remember(shortcut.longLabel, shortcut.shortLabel) { mutableStateOf(true) }
    val label: String = if (useLongLabel) shortcut.longLabel else shortcut.shortLabel
    Text(
        modifier = modifier,
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
internal fun PopupEmptyState(messageResId: Int, onTurnOn: (() -> Unit)? = null, onAddApps: (() -> Unit)? = null) {
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
        if (onAddApps != null) {
            Spacer(Modifier.height(addAppsButtonTopPadding))
            FilledTonalButton(onClick = onAddApps) {
                Text(stringResource(R.string.add_apps))
            }
        }
    }
}
