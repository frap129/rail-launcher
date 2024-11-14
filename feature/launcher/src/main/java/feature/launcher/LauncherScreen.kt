package feature.launcher

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import core.data.apps.App
import core.data.launcher.model.LauncherItem
import core.data.launcher.model.LauncherItemGroup
import core.ui.composables.OutlinedText
import core.ui.composables.scrollrail.ScrollRail
import core.ui.model.data.Destination
import core.util.screenHeightDp
import org.koin.androidx.compose.koinViewModel

val launcherDestination = Destination(
    route = "launcher",
    content = { navController, _ ->
        LauncherScreen(navController)
    }
)

@Composable
fun LauncherScreen(navController: NavController, viewModel: LauncherViewModel = koinViewModel()) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val launcherItemGroups = viewModel.launcherItemGroups.collectAsState(emptyList())

    Box {
        when (uiState) {
            is LauncherUiState.AppList -> LauncherList(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp, 0.dp),
                launcherList = (uiState as LauncherUiState.AppList).groups,
                lazyListState = (uiState as LauncherUiState.AppList).lazyListState
            )

            is LauncherUiState.Scrolling -> {
                LauncherItemGroup(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            start = 32.dp,
                            end = 32.dp,
                            top = (screenHeightDp(context) / 5).dp
                        ),
                    group = (uiState as LauncherUiState.Scrolling).group
                )
            }

            LauncherUiState.Error -> {}

            LauncherUiState.Loading -> {}
        }

        ScrollRail(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(0.dp, 200.dp, 0.dp, 0.dp)
                .width(64.dp)
                .offset { IntOffset(-16.dp.toPx().toInt(), 0) },
            items = launcherItemGroups.value.map { it.name },
            onScrollStarted = { viewModel.onScrollStarted(it) },
            onScroll = { viewModel.onScroll(it) },
            onScrollEnded = { viewModel.onScrollEnded(it) }
        )
        if (viewModel.bottomSheetState.value != BottomSheetStatus.CLOSED) {
            val selectedItem = viewModel.bottomSheetItem.value?.collectAsState(null)
            if (selectedItem?.value != null) {
                LauncherItemBottomSheet(
                    item = selectedItem.value!!,
                    onDismissRequest = { viewModel.closeItemMenu() }
                )
            }
        }
    }
}

@Composable
fun LauncherList(modifier: Modifier = Modifier, launcherList: List<LauncherItemGroup>, lazyListState: LazyListState) {
    val context = LocalContext.current
    val defaultBuffer = 100.dp
    val scrollingTopBuffer = (screenHeightDp(context) / 5).dp
    val scrollingBottomBuffer = (screenHeightDp(context) / 5 * 4).dp
    var animated by remember { mutableStateOf(false) }

    val topSpacerHeightAnimator by animateDpAsState(
        targetValue = if (animated) defaultBuffer else scrollingTopBuffer,
        animationSpec = tween(durationMillis = 200)
    )
    val bottomSpacerHeightAnimator by animateDpAsState(
        targetValue = if (animated) defaultBuffer else scrollingBottomBuffer,
        animationSpec = tween(delayMillis = 10, durationMillis = 300)
    )

    LazyColumn(
        modifier = modifier
            .testTag("launcherList"),
        state = lazyListState,
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Top
    ) {
        item {
            Spacer(Modifier.height(topSpacerHeightAnimator))
        }
        items(launcherList, key = { it.name }) { itemGroup ->
            LauncherItemGroup(group = itemGroup)
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
fun LauncherItemGroup(modifier: Modifier = Modifier, group: LauncherItemGroup) {
    Column(modifier = modifier) {
        OutlinedText(
            text = group.name,
            fontSize = 26.sp,
            textAlign = TextAlign.Start,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp, 24.dp, 0.dp, 8.dp)
        )
        group.items.forEach { item ->
            LauncherItem(item)
        }
    }
}

@Composable
fun LauncherItem(item: LauncherItem, viewModel: LauncherViewModel = koinViewModel()) {
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { item.launch(context) },
                    onLongPress = {
                        viewModel.openItemMenu(item)
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                )
            }
    ) {
        Image(
            modifier = Modifier.size(64.dp).padding(10.dp),
            painter = rememberDrawablePainter(item.getIcon(context)),
            contentDescription = item.name
        )
        OutlinedText(
            text = item.name,
            fontSize = 18.sp
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LauncherItemBottomSheet(item: LauncherItem, onDismissRequest: () -> Unit, viewModel: LauncherViewModel = koinViewModel()) {
    ModalBottomSheet(
        dragHandle = { Spacer(Modifier.fillMaxWidth().height(10.dp)) },
        onDismissRequest = onDismissRequest
    ) {
        if (viewModel.bottomSheetState.value == BottomSheetStatus.MENU) {
            LauncherItemMenu(item)
        } else if (viewModel.bottomSheetState.value == BottomSheetStatus.RENAME) {
            LauncherItemEditName(item)
        }
    }
}

@Composable
fun LauncherItemMenu(item: LauncherItem, viewModel: LauncherViewModel = koinViewModel()) {
    val context = LocalContext.current

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp, 10.dp)
    ) {
        Image(
            modifier = Modifier
                .size(64.dp)
                .padding(10.dp),
            painter = rememberDrawablePainter(item.getIcon(context)),
            contentDescription = item.name
        )

        Text(
            text = item.name,
            fontSize = 28.sp,
            modifier = Modifier.clickable {
                viewModel.openItemRename()
            }
        )
    }

    if (item is App) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp, 10.dp).clickable {
                    item.openAppInfo(context)
                }
        ) {
            Image(
                modifier = Modifier.size(48.dp).padding(10.dp),
                painter = painterResource(android.R.drawable.ic_dialog_info),
                contentDescription = "App Info"
            )
            Text(
                text = "App Info",
                fontSize = 20.sp
            )
        }
    }
}

@Composable
fun LauncherItemEditName(item: LauncherItem, viewModel: LauncherViewModel = koinViewModel()) {
    val context = LocalContext.current
    var nameEdit by remember { mutableStateOf(item.name) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
            modifier = Modifier
                .padding(20.dp, 10.dp)
        ) {
            Image(
                modifier = Modifier
                    .size(64.dp)
                    .padding(10.dp),
                painter = rememberDrawablePainter(item.getIcon(context)),
                contentDescription = item.name
            )
            Text(
                text = "Rename ${item.name}",
                fontSize = 24.sp
            )
        }

        OutlinedTextField(
            modifier = Modifier
                .padding(48.dp, 10.dp)
                .fillMaxWidth(),
            singleLine = true,
            shape = CircleShape,
            textStyle = LocalTextStyle.current.copy(fontSize = 18.sp),
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
                onClick = {
                    nameEdit = item.defaultName
                }
            ) {
                Text(
                    text = "Reset",
                    fontSize = 18.sp
                )
            }

            TextButton(
                onClick = {
                    viewModel.setItemName(item, nameEdit)
                    viewModel.bottomSheetState.value = BottomSheetStatus.MENU
                }
            ) {
                Text(
                    text = "Done",
                    fontSize = 18.sp
                )
            }
        }
    }
}
