package feature.launcher

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
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
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.InfoCircle
import core.data.apps.model.App
import core.data.launchables.model.Launchable
import core.data.launchables.model.LaunchableGroup
import core.ui.composables.FocusRevealContent
import core.ui.composables.OutlinedText
import core.ui.composables.scrollrail.ScrollRail
import core.ui.model.data.Destination
import core.ui.model.data.HOME_INTENT_REQUEST_KEY
import core.util.toDp
import kotlinx.coroutines.flow.collect
import org.koin.androidx.compose.koinViewModel
import timber.log.Timber

val launcherDestination = Destination(
    route = "launcher",
    content = { navController, backStackEntry ->
        LauncherScreen(navController, backStackEntry)
    }
)

private val homeEdgePadding = 32.dp
private val homeRailTouchInset = 80.dp

private fun dispatchRailGroup(groups: List<LaunchableGroup>, index: Int, action: (LaunchableGroup) -> Unit) {
    val group: LaunchableGroup? = groups.getOrNull(index)
    if (group == null) {
        Timber.w("Ignoring Rail index %d outside %d groups", index, groups.size)
        return
    }
    action(group)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LauncherScreen(navController: NavController, backStackEntry: NavBackStackEntry, viewModel: LauncherViewModel = koinViewModel()) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val uiState by viewModel.uiState.collectAsState()
    val menuState by viewModel.menuState.collectAsState()
    val railGroups: State<List<LaunchableGroup>> = viewModel.launchableGroups.collectAsState(emptyList())
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

    BackHandler {
        if (!viewModel.onBack()) {
            activity?.finish()
        }
    }

    Box(Modifier.fillMaxSize()) {
        FocusRevealContent(
            modifier = Modifier.fillMaxSize(),
            targetState = uiState,
            focusState = LauncherUiState.AppList.Scrolling::class,
            revealState = LauncherUiState.AppList.Browsing::class,
            defaultSpec = fadeIn(animationSpec = tween(5, 5))
                .togetherWith(fadeOut(animationSpec = tween(5))),
            focusSpec = fadeIn(animationSpec = tween(200))
                .togetherWith(fadeOut(animationSpec = tween(1))),
            revealSpec = fadeIn(animationSpec = tween(200, 75))
                .togetherWith(fadeOut(animationSpec = tween(1, 275))),
            contentKey = {
                when (it) {
                    is LauncherUiState.AppList.Browsing -> LauncherUiState.AppList.Browsing::class
                    is LauncherUiState.Home -> LauncherUiState.Home::class
                    else -> it
                }
            }
        ) { state ->
            when (state) {
                is LauncherUiState.Home -> LauncherHome(
                    modifier = Modifier.fillMaxSize(),
                    home = state
                )

                is LauncherUiState.AppList.Browsing -> LauncherList(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp),
                    launcherList = state.groups,
                    lazyListState = state.lazyListState,
                    onItemLaunch = launchItem,
                    onItemLongPress = { viewModel.openItemMenu(it) },
                    topOffset = viewModel.listStartOffsetPx.toDp(context).dp,
                    bottomOffset = viewModel.listEndOffsetPx.toDp(context).dp
                )

                is LauncherUiState.AppList.Scrolling -> LauncherItemGroup(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 32.dp, top = viewModel.listStartOffsetPx.toDp(context).dp),
                    group = state.group,
                    onItemLaunch = launchItem,
                    onItemLongPress = { viewModel.openItemMenu(it) }
                )

                LauncherUiState.Error -> {}

                LauncherUiState.Loading -> {}
            }
        }

        ScrollRail(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(0.dp, 200.dp, 0.dp, 0.dp)
                .width(64.dp)
                .offset { IntOffset(-16.dp.toPx().toInt(), 0) },
            items = railGroups.value.map { group -> group.label },
            onScrollStarted = { index -> dispatchRailGroup(railGroups.value, index, viewModel::onRailTouched) },
            onScroll = { index -> dispatchRailGroup(railGroups.value, index, viewModel::onRailMoved) },
            onScrollEnded = { index -> dispatchRailGroup(railGroups.value, index, viewModel::onRailReleased) }
        )

        if (menuState !is LauncherMenuState.Closed) {
            ModalBottomSheet(
                dragHandle = { Spacer(Modifier.fillMaxWidth().height(10.dp)) },
                onDismissRequest = { viewModel.closeItemMenu() }
            ) {
                when (menuState) {
                    is LauncherMenuState.Menu -> LauncherItemMenu((menuState as LauncherMenuState.Menu).item)
                    is LauncherMenuState.Rename -> LauncherItemEditName((menuState as LauncherMenuState.Rename).item)
                    LauncherMenuState.Closed -> {} // this state is never reached
                }
            }
        }
    }
}

@Composable
fun LauncherHome(modifier: Modifier = Modifier, home: LauncherUiState.Home, widgetContent: @Composable (Widget) -> Unit = {}) {
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
            item {
                OutlinedText(
                    text = "Long-press any app to add it to Favorites",
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

@Composable
fun LauncherList(
    modifier: Modifier = Modifier,
    launcherList: List<LaunchableGroup.Listed>,
    lazyListState: LazyListState,
    onItemLaunch: (Launchable) -> Unit,
    onItemLongPress: (Launchable) -> Unit,
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
                onItemLaunch = onItemLaunch,
                onItemLongPress = onItemLongPress
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
    onItemLaunch: (Launchable) -> Unit,
    onItemLongPress: (Launchable) -> Unit
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
            LauncherItem(item, onItemLaunch, onItemLongPress)
        }
    }
}

@Composable
fun LauncherItem(item: Launchable, onItemLaunch: (Launchable) -> Unit, onItemLongPress: (Launchable) -> Unit) {
    val hapticFeedback = LocalHapticFeedback.current

    CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.titleMedium) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onItemLaunch(item) },
                        onLongPress = {
                            onItemLongPress(item)
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    )
                }
        ) {
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
        if (item is App) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { item.openAppInfo(context) }
                    .padding(20.dp, 10.dp)
            ) {
                Image(
                    modifier = Modifier.size(56.dp).padding(10.dp),
                    imageVector = FontAwesomeIcons.Solid.InfoCircle,
                    colorFilter = ColorFilter.tint(LocalContentColor.current),
                    contentDescription = "App Info"
                )
                Text("App Info")
            }
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
