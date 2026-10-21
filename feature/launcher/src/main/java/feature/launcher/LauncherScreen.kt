package feature.launcher

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Regular
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.regular.Star
import compose.icons.fontawesomeicons.solid.GripVertical
import compose.icons.fontawesomeicons.solid.InfoCircle
import compose.icons.fontawesomeicons.solid.Star
import core.data.apps.model.App
import core.data.launchables.model.Launchable
import core.data.launchables.model.LaunchableGroup
import core.ui.composables.FocusRevealContent
import core.ui.composables.OutlinedText
import core.ui.composables.scrollrail.ScrollRail
import core.ui.model.data.Destination
import core.ui.model.data.HOME_INTENT_REQUEST_KEY
import core.util.toDp
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import org.koin.androidx.compose.koinViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

val launcherDestination = Destination(
    route = "launcher",
    content = { navController, backStackEntry ->
        LauncherScreen(navController, backStackEntry)
    }
)

private val homeEdgePadding = 32.dp
private val homeRailTouchInset = 80.dp
private val dragHandleSize = 48.dp
private val dragHandleDotRadius = 3.dp
private val dragHandleDotSpacing = 12.dp
private val dragHandleOutlineWidth = 2.dp
private val dragHandleRailGap = 16.dp
private val doneButtonPadding = 24.dp
private val favoriteStarSize = 28.dp
private val favoriteStarOutlineSize = 36.dp
private val contentHorizontalPadding: Dp = 32.dp
private val railTopPadding: Dp = 200.dp
private val railWidth: Dp = 64.dp
private val railHorizontalOffset: Dp = (-16).dp
private val sheetDragHandleHeight: Dp = 10.dp

private const val DEFAULT_FADE_IN_MILLIS: Int = 5
private const val DEFAULT_FADE_IN_DELAY_MILLIS: Int = 5
private const val DEFAULT_FADE_OUT_MILLIS: Int = 5
private const val FOCUS_FADE_IN_MILLIS: Int = 200
private const val FOCUS_FADE_OUT_MILLIS: Int = 1
private const val REVEAL_FADE_IN_MILLIS: Int = 200
private const val REVEAL_FADE_IN_DELAY_MILLIS: Int = 75
private const val REVEAL_FADE_OUT_MILLIS: Int = 1
private const val REVEAL_FADE_OUT_DELAY_MILLIS: Int = 275

@Immutable
data class LauncherItemActions(
    val onItemLaunch: (Launchable) -> Unit,
    val onItemLongPress: (Launchable) -> Unit,
    val onToggleFavorite: (Launchable) -> Unit
)

@Immutable
data class LauncherRowState(val isEditingFavorites: Boolean, val favoriteKeys: Set<String>)

// The Rail's touch column starts homeRailTouchInset from the end edge.
private val dragHandleEndPadding = homeRailTouchInset - homeEdgePadding + dragHandleRailGap

@Composable
fun LauncherScreen(navController: NavController, backStackEntry: NavBackStackEntry, viewModel: LauncherViewModel = koinViewModel()) {
    val context: Context = LocalContext.current
    val launchItem: (Launchable) -> Unit = { launchable ->
        launchable.launch(context)
        viewModel.onLaunchableLaunched()
    }
    LaunchedEffect(backStackEntry) {
        backStackEntry.savedStateHandle
            .getStateFlow(HOME_INTENT_REQUEST_KEY, false)
            .collect { requested ->
                if (requested) {
                    backStackEntry.savedStateHandle[HOME_INTENT_REQUEST_KEY] = false
                    viewModel.onHomeIntent()
                }
            }
    }
    BackHandler { viewModel.onBack() }
    Box(Modifier.fillMaxSize()) {
        LauncherContent(
            modifier = Modifier.fillMaxSize(),
            viewModel = viewModel,
            onItemLaunch = launchItem
        )
        LauncherRail(viewModel)
        LauncherDoneButton(viewModel)
        LauncherMenu(viewModel)
    }
}

@Composable
private fun LauncherContent(modifier: Modifier, viewModel: LauncherViewModel, onItemLaunch: (Launchable) -> Unit) {
    val uiState: LauncherUiState by viewModel.uiState.collectAsState()
    val context: Context = LocalContext.current
    val itemActions: LauncherItemActions = remember(viewModel, onItemLaunch) {
        LauncherItemActions(
            onItemLaunch = onItemLaunch,
            onItemLongPress = viewModel::openItemMenu,
            onToggleFavorite = viewModel::toggleFavorite
        )
    }
    val home: LauncherUiState.Home? = retainedHomeState(uiState)
    val isHomeVisible: Boolean = uiState is LauncherUiState.Home
    val homeAlpha: State<Float> = animateFloatAsState(
        targetValue = if (isHomeVisible) 1f else 0f,
        animationSpec = if (isHomeVisible) {
            tween(durationMillis = DEFAULT_FADE_IN_MILLIS, delayMillis = DEFAULT_FADE_IN_DELAY_MILLIS)
        } else {
            tween(durationMillis = DEFAULT_FADE_OUT_MILLIS)
        },
        label = "homeAlpha"
    )
    val hiddenHomeModifier: Modifier = remember {
        Modifier
            .clearAndSetSemantics { hideFromAccessibility() }
            .consumePointerInput()
    }
    val homePaint: Paint = remember { Paint() }
    Box(modifier) {
        // Kept composed under the App List so returning to Home never rebuilds it.
        if (home != null) {
            LauncherHome(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        val alpha: Float = homeAlpha.value
                        when {
                            alpha <= 0f -> Unit

                            alpha >= 1f -> drawContent()

                            else -> {
                                homePaint.alpha = alpha
                                drawContext.canvas.saveLayer(Rect(Offset.Zero, size), homePaint)
                                drawContent()
                                drawContext.canvas.restore()
                            }
                        }
                    }
                    .then(if (isHomeVisible) Modifier else hiddenHomeModifier),
                home = home,
                itemActions = itemActions,
                onFavoriteMoved = viewModel::moveFavorite
            )
        }
        FocusRevealContent(
            modifier = Modifier.fillMaxSize(),
            targetState = uiState,
            focusState = LauncherUiState.AppList.Scrolling::class,
            revealState = LauncherUiState.AppList.Browsing::class,
            defaultSpec = fadeIn(animationSpec = tween(durationMillis = DEFAULT_FADE_IN_MILLIS, delayMillis = DEFAULT_FADE_IN_DELAY_MILLIS))
                .togetherWith(fadeOut(animationSpec = tween(durationMillis = DEFAULT_FADE_OUT_MILLIS))),
            focusSpec = fadeIn(animationSpec = tween(durationMillis = FOCUS_FADE_IN_MILLIS))
                .togetherWith(fadeOut(animationSpec = tween(durationMillis = FOCUS_FADE_OUT_MILLIS))),
            revealSpec = fadeIn(animationSpec = tween(durationMillis = REVEAL_FADE_IN_MILLIS, delayMillis = REVEAL_FADE_IN_DELAY_MILLIS))
                .togetherWith(
                    fadeOut(animationSpec = tween(durationMillis = REVEAL_FADE_OUT_MILLIS, delayMillis = REVEAL_FADE_OUT_DELAY_MILLIS))
                ),
            contentKey = { state ->
                when (state) {
                    is LauncherUiState.AppList.Browsing -> LauncherUiState.AppList.Browsing::class
                    is LauncherUiState.AppList.Scrolling -> state.group
                    is LauncherUiState.Home -> LauncherUiState.Home::class
                    LauncherUiState.Loading -> LauncherUiState.Loading
                }
            }
        ) { state ->
            when (state) {
                is LauncherUiState.Home, LauncherUiState.Loading -> {}

                is LauncherUiState.AppList.Browsing -> {
                    val rowState: LauncherRowState = rememberRowState(state.isEditingFavorites, viewModel.favorites)
                    LauncherList(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = contentHorizontalPadding),
                        launcherList = state.groups,
                        lazyListState = state.lazyListState,
                        rowState = rowState,
                        itemActions = itemActions,
                        topOffset = viewModel.listStartOffsetPx.toDp(context).dp,
                        bottomOffset = viewModel.listEndOffsetPx.toDp(context).dp
                    )
                }

                is LauncherUiState.AppList.Scrolling -> {
                    val rowState: LauncherRowState = rememberRowState(state.isEditingFavorites, viewModel.favorites)
                    LauncherItemGroup(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = contentHorizontalPadding, top = viewModel.listStartOffsetPx.toDp(context).dp),
                        group = state.group,
                        rowState = rowState,
                        itemActions = itemActions
                    )
                }
            }
        }
    }
}

// Keeps the latest Home state while the App List covers Home.
@Composable
private fun retainedHomeState(uiState: LauncherUiState): LauncherUiState.Home? {
    var retained: LauncherUiState.Home? by remember { mutableStateOf<LauncherUiState.Home?>(null) }
    SideEffect {
        if (uiState is LauncherUiState.Home) {
            retained = uiState
        }
    }
    return when (uiState) {
        is LauncherUiState.Home -> uiState
        is LauncherUiState.AppList -> retained
        LauncherUiState.Loading -> null
    }
}

// Consumes touches that miss the App List and land on the hidden Home.
private fun Modifier.consumePointerInput(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent(PointerEventPass.Initial).changes.forEach { change -> change.consume() }
        }
    }
}

// App List rows only read Favorites while Edit Favorites shows stars.
@Composable
private fun rememberRowState(isEditingFavorites: Boolean, favorites: StateFlow<List<Launchable>>): LauncherRowState {
    if (!isEditingFavorites) {
        return LauncherRowState(isEditingFavorites = false, favoriteKeys = emptySet())
    }
    val favoriteList: List<Launchable> by favorites.collectAsState()
    val favoriteKeys: Set<String> = remember(favoriteList) { favoriteList.mapTo(HashSet()) { favorite -> favorite.key } }
    return LauncherRowState(isEditingFavorites = true, favoriteKeys = favoriteKeys)
}

@Composable
private fun BoxScope.LauncherRail(viewModel: LauncherViewModel) {
    val railLabels: List<String> by viewModel.railLabels.collectAsState()
    ScrollRail(
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .padding(0.dp, railTopPadding, 0.dp, 0.dp)
            .width(railWidth)
            .offset { IntOffset(railHorizontalOffset.toPx().toInt(), 0) },
        items = railLabels,
        onScroll = viewModel::onRailPreview,
        onScrollEnded = viewModel::onRailReleased
    )
}

@Composable
private fun BoxScope.LauncherDoneButton(viewModel: LauncherViewModel) {
    val isEditingFavorites: Boolean by viewModel.isEditingFavorites.collectAsState()
    if (isEditingFavorites) {
        FloatingActionButton(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .safeDrawingPadding()
                .padding(doneButtonPadding),
            onClick = viewModel::finishEditingFavorites
        ) {
            Text("Done")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LauncherMenu(viewModel: LauncherViewModel) {
    val menuState: LauncherMenuState by viewModel.menuState.collectAsState()
    if (menuState is LauncherMenuState.Closed) {
        return
    }
    ModalBottomSheet(
        dragHandle = { Spacer(Modifier.fillMaxWidth().height(sheetDragHandleHeight)) },
        onDismissRequest = { viewModel.closeItemMenu() }
    ) {
        when (val currentMenuState: LauncherMenuState = menuState) {
            is LauncherMenuState.Menu -> LauncherItemMenu(item = currentMenuState.item, viewModel = viewModel)
            is LauncherMenuState.Rename -> LauncherItemEditName(item = currentMenuState.item, viewModel = viewModel)
            LauncherMenuState.Closed -> {} // this state is never reached
        }
    }
}

@Composable
fun LauncherHome(
    modifier: Modifier = Modifier,
    home: LauncherUiState.Home,
    itemActions: LauncherItemActions,
    onFavoriteMoved: (fromIndex: Int, toIndex: Int) -> Unit,
    widgetContent: @Composable (Widget) -> Unit = {}
) {
    val reorderableListState = rememberReorderableLazyListState(home.lazyListState) { from, to ->
        onFavoriteMoved(from.index, to.index)
    }
    Column(modifier = modifier.fillMaxSize().safeDrawingPadding()) {
        // The end inset keeps widgets clear of the Rail's touch column.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = homeEdgePadding, top = homeEdgePadding, end = homeRailTouchInset)
        ) {
            home.widgets.forEach { widget -> widgetContent(widget) }
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .testTag("launcherHome"),
            state = home.lazyListState,
            verticalArrangement = Arrangement.Bottom,
            contentPadding = PaddingValues(start = homeEdgePadding, end = homeEdgePadding, bottom = homeEdgePadding)
        ) {
            if (home.favorites.isEmpty()) {
                item {
                    OutlinedText(
                        text = "Long-press any app to add it to Favorites",
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            } else {
                items(home.favorites, key = { favorite -> favorite.key }) { favorite ->
                    ReorderableItem(reorderableListState, key = favorite.key, enabled = home.isEditingFavorites) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LauncherItem(
                                item = favorite,
                                isEditingFavorites = home.isEditingFavorites,
                                isFavorite = true,
                                itemActions = itemActions,
                                modifier = Modifier.weight(1f)
                            )
                            if (home.isEditingFavorites) {
                                FavoriteDragHandle(
                                    modifier = Modifier
                                        .padding(end = dragHandleEndPadding)
                                        .draggableHandle(),
                                    contentDescription = "Reorder ${favorite.name}"
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// A four-dot grip outlined like Rail text so it reads on any wallpaper.
@Composable
private fun FavoriteDragHandle(modifier: Modifier = Modifier, contentDescription: String) {
    val fillColor: Color = MaterialTheme.colorScheme.onSurface
    val outlineColor: Color = MaterialTheme.colorScheme.surfaceContainerLowest
    Canvas(
        modifier = modifier
            .size(dragHandleSize)
            .semantics { this.contentDescription = contentDescription }
    ) {
        val dotRadius: Float = dragHandleDotRadius.toPx()
        val dotSpacing: Float = dragHandleDotSpacing.toPx()
        val outline: Stroke = Stroke(width = dragHandleOutlineWidth.toPx())
        listOf(-1f, 1f).forEach { column ->
            listOf(-1f, 1f).forEach { row ->
                val dotCenter: Offset = center + Offset(column * dotSpacing / 2, row * dotSpacing / 2)
                drawCircle(color = outlineColor, radius = dotRadius, center = dotCenter, style = outline)
                drawCircle(color = fillColor, radius = dotRadius, center = dotCenter)
            }
        }
    }
}

@Composable
fun LauncherList(
    modifier: Modifier = Modifier,
    launcherList: List<LaunchableGroup.Listed>,
    lazyListState: LazyListState,
    rowState: LauncherRowState,
    itemActions: LauncherItemActions,
    topOffset: Dp,
    bottomOffset: Dp
) {
    val defaultBuffer = 100.dp
    var animated by remember { mutableStateOf(false) }
    val topSpacerHeightAnimator by animateDpAsState(
        targetValue = if (animated) defaultBuffer else topOffset,
        animationSpec = tween(durationMillis = 100, delayMillis = 275)
    )
    val bottomSpacerHeightAnimator by animateDpAsState(
        targetValue = if (animated) defaultBuffer else bottomOffset,
        animationSpec = tween(durationMillis = 400, delayMillis = 275)
    )
    LazyColumn(
        modifier = modifier.testTag("launcherList"),
        state = lazyListState,
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Top
    ) {
        item {
            Spacer(Modifier.height(topSpacerHeightAnimator))
        }
        items(launcherList, key = { it.label }) { itemGroup ->
            LauncherItemGroup(
                group = itemGroup,
                rowState = rowState,
                itemActions = itemActions
            )
        }
        item {
            Spacer(Modifier.height(bottomSpacerHeightAnimator))
        }
    }
    LaunchedEffect(Unit) {
        animated = true
    }
}

@Composable
fun LauncherItemGroup(
    modifier: Modifier = Modifier,
    group: LaunchableGroup.Listed,
    rowState: LauncherRowState,
    itemActions: LauncherItemActions
) {
    Column(modifier = modifier) {
        CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.headlineMedium) {
            OutlinedText(
                text = group.label,
                textAlign = TextAlign.Start,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp, 24.dp, 0.dp, 8.dp)
            )
        }
        group.items.forEach { item ->
            LauncherItem(
                item = item,
                isEditingFavorites = rowState.isEditingFavorites,
                isFavorite = item.key in rowState.favoriteKeys,
                itemActions = itemActions
            )
        }
    }
}

@Composable
fun LauncherItem(
    item: Launchable,
    isEditingFavorites: Boolean,
    isFavorite: Boolean,
    itemActions: LauncherItemActions,
    modifier: Modifier = Modifier
) {
    val hapticFeedback = LocalHapticFeedback.current
    val currentItem: Launchable by rememberUpdatedState(item)
    val currentItemActions: LauncherItemActions by rememberUpdatedState(itemActions)
    CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.titleMedium) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
            modifier = modifier
                .fillMaxWidth()
                .pointerInput(isEditingFavorites) {
                    if (!isEditingFavorites) {
                        detectTapGestures(
                            onTap = { currentItemActions.onItemLaunch(currentItem) },
                            onLongPress = {
                                currentItemActions.onItemLongPress(currentItem)
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        )
                    }
                }
        ) {
            if (isEditingFavorites) {
                FavoriteToggleStar(
                    isFavorite = isFavorite,
                    onClick = { currentItemActions.onToggleFavorite(currentItem) }
                )
            }
            AsyncImage(
                modifier = Modifier.size(64.dp).padding(10.dp),
                model = item.icon,
                contentDescription = item.name
            )
            OutlinedText(
                text = item.name
            )
        }
    }
}

@Composable
private fun FavoriteToggleStar(isFavorite: Boolean, onClick: () -> Unit) {
    val label: String = if (isFavorite) "Remove from Favorites" else "Add to Favorites"
    val imageVector: ImageVector = if (isFavorite) FontAwesomeIcons.Solid.Star else FontAwesomeIcons.Regular.Star
    Box(
        modifier = Modifier
            .size(dragHandleSize)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        Image(
            modifier = Modifier.size(favoriteStarOutlineSize),
            imageVector = imageVector,
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.surfaceContainerLowest),
            contentDescription = null
        )
        Image(
            modifier = Modifier.size(favoriteStarSize),
            imageVector = imageVector,
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
            contentDescription = null
        )
    }
}

@Composable
private fun LauncherMenuAction(imageVector: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(20.dp, 10.dp)
    ) {
        Image(
            modifier = Modifier.size(56.dp).padding(10.dp),
            imageVector = imageVector,
            colorFilter = ColorFilter.tint(LocalContentColor.current),
            contentDescription = label
        )
        Text(label)
    }
}

@Composable
fun LauncherItemMenu(item: Launchable, viewModel: LauncherViewModel = koinViewModel()) {
    val context = LocalContext.current

    CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.headlineMedium) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp, 10.dp)
        ) {
            AsyncImage(
                modifier = Modifier
                    .size(64.dp)
                    .padding(10.dp),
                model = item.icon,
                contentDescription = item.name
            )

            Text(
                text = item.name,
                fontSize = 28.sp,
                modifier = Modifier.clickable {
                    viewModel.openItemRename(item)
                }
            )
        }
    }

    CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.titleLarge) {
        LauncherMenuAction(
            imageVector = FontAwesomeIcons.Solid.GripVertical,
            label = "Edit Favorites",
            onClick = { viewModel.editFavorites() }
        )
        if (item is App) {
            LauncherMenuAction(
                imageVector = FontAwesomeIcons.Solid.InfoCircle,
                label = "App Info",
                onClick = { item.openAppInfo(context) }
            )
        }
    }
}

@Composable
fun LauncherItemEditName(item: Launchable, viewModel: LauncherViewModel = koinViewModel()) {
    var nameEdit by remember { mutableStateOf(item.name) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.headlineMedium) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start,
                modifier = Modifier
                    .padding(20.dp, 10.dp)
            ) {
                AsyncImage(
                    modifier = Modifier
                        .size(64.dp)
                        .padding(10.dp),
                    model = item.icon,
                    contentDescription = item.name
                )
                Text("Rename ${item.name}")
            }
        }

        CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.titleMedium) {
            OutlinedTextField(
                modifier = Modifier
                    .padding(48.dp, 10.dp)
                    .fillMaxWidth(),
                singleLine = true,
                shape = CircleShape,
                value = nameEdit,
                onValueChange = { nameEdit = it },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TextButton(
                    onClick = { nameEdit = item.defaultName }
                ) {
                    Text("Reset")
                }

                TextButton(
                    onClick = { viewModel.setItemName(item, nameEdit) }
                ) {
                    Text("Done")
                }
            }
        }
    }
}
