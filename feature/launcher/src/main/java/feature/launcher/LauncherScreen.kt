package feature.launcher

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.accompanist.drawablepainter.DrawablePainter
import core.data.apps.App
import core.data.rail.RailItem
import core.ui.composables.scrollrail.ScrollRail
import core.ui.model.data.Destination
import core.util.screenHeightDp
import kotlinx.coroutines.launch
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
    val launcherItems = viewModel.launcherItems.collectAsState(emptyMap<Char, List<RailItem>>())
    val launcherScrollState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val visibilities: MutableMap<Char, MutableState<Boolean>> = mutableMapOf()
    val railHelper = LauncherScrollRailHelper(
        context = context,
        railItems = launcherItems.value.keys.toList(),
        listState = launcherScrollState,
        visibilities = visibilities
    )

    Box {
        LazyColumn(
            modifier = Modifier.fillMaxSize().align(Alignment.Center).padding(32.dp, 0.dp),
            state = launcherScrollState,
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.Top
        ) {
            item {
                Spacer(Modifier.height((screenHeightDp(context) / 4).dp))
            }
            items(launcherItems.value.keys.toList()) { key ->

                visibilities[key] = remember { mutableStateOf(true) }
                Column(
                    modifier = Modifier
                        .alpha(if (visibilities[key]?.value != false) 1f else 0f)
                ) {
                    LauncherItemGroup(
                        label = "$key",
                        items = launcherItems.value[key]!!
                    )
                }
            }
            item {
                Spacer(Modifier.height((screenHeightDp(context) / 4).dp))
            }
        }
        ScrollRail(
            scrollRailHelper = railHelper,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(0.dp, 200.dp, 0.dp, 0.dp)
                .width(64.dp)
                .offset { IntOffset(-16.dp.toPx().toInt(), 0) }
        )
    }

    // Scroll partway down the initial buffer item
    SideEffect {
        scope.launch {
            launcherScrollState.scrollToItem(
                index = 1,
                scrollOffset = -(screenHeightDp(context) / 8).toInt()
            )
        }
    }
}

@Composable
fun LauncherItemGroup(label: String, items: List<RailItem>) {
    Text(
        text = label,
        fontSize = 26.sp,
        textAlign = TextAlign.Start,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp, 24.dp, 0.dp, 8.dp)
    )
    Column(
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Top
    ) {
        items.forEach { item ->
            LauncherItem(item)
        }
    }
}

@Composable
fun LauncherItem(item: RailItem) {
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    var showBottomSheet by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { item.launch(context) },
                    onLongPress = {
                        showBottomSheet = true
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                )
            }
    ) {
        Image(
            modifier = Modifier.size(64.dp).padding(10.dp),
            painter = DrawablePainter(item.getIcon(context)),
            contentDescription = item.name
        )
        Text(
            text = item.name,
            fontSize = 20.sp
        )
        if (showBottomSheet) {
            LauncherItemBottomSheet(
                item = item,
                onDismissRequest = { showBottomSheet = false }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LauncherItemBottomSheet(item: RailItem, onDismissRequest: () -> Unit) {
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismissRequest
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp, 10.dp)
        ) {
            Image(
                modifier = Modifier.size(64.dp).padding(10.dp),
                painter = DrawablePainter(item.getIcon(context)),
                contentDescription = item.name
            )
            Text(
                text = item.name,
                fontSize = 28.sp
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
}
