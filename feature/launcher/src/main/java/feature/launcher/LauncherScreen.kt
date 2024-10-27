package feature.launcher

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.accompanist.drawablepainter.DrawablePainter
import core.apprepo.App
import core.ui.model.data.Destination
import kotlin.math.max
import kotlin.math.min
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
    val launcherItems = viewModel.launcherItems.collectAsState(emptyMap<Char, List<App>>())
    val launcherScrollState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val visibilities: MutableMap<Char, MutableState<Boolean>> = mutableMapOf()

    Box {
        LazyColumn(
            modifier = Modifier.fillMaxSize().align(Alignment.Center).padding(64.dp, 0.dp),
            state = launcherScrollState,
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.Top
        ) {
            item {
                Spacer(Modifier.height((viewModel.heightInDp / 4).dp))
            }
            items(launcherItems.value.keys.toList()) { key ->
                visibilities[key] = itemGroup(
                    label = "$key",
                    items = launcherItems.value[key]!!
                )
            }
            item {
                Spacer(Modifier.height((viewModel.heightInDp / 4).dp))
            }
        }
        AlphaNumericScrollbar(
            modifier = Modifier.align(Alignment.CenterEnd),
            listState = launcherScrollState,
            keys = launcherItems.value.keys.toList(),
            visibilities = visibilities
        )
    }

    // Scroll partway down the inital buffer item
    SideEffect {
        scope.launch {
            launcherScrollState.scrollToItem(
                index = 1,
                scrollOffset = -(viewModel.heightInPixels / 6)
            )
        }
    }
}

@Composable
fun itemGroup(label: String, items: List<App>): MutableState<Boolean> {
    val context = LocalContext.current
    val visible = remember { mutableStateOf(true) }

    Text(
        text = label,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .padding(20.dp, 24.dp, 0.dp, 8.dp)
            .alpha(if (visible.value) 1f else 0f)
    )
    Column(
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Top,
        modifier = Modifier.alpha(if (visible.value) 1f else 0f)
    ) {
        items.forEach { item ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start,
                modifier = Modifier.clickable {
                    item.launch(context)
                }
            ) {
                Image(
                    modifier = Modifier.size(64.dp).padding(8.dp),
                    painter = DrawablePainter(item.getIcon(context)),
                    contentDescription = item.label
                )
                Text(item.label)
            }
        }
    }
    return visible
}

@Composable
fun AlphaNumericScrollbar(
    modifier: Modifier = Modifier,
    listState: LazyListState,
    keys: List<Char>,
    visibilities: MutableMap<Char, MutableState<Boolean>>
) {
    val viewModel = koinViewModel<LauncherViewModel>()
    var selectedItemIndex by remember { mutableIntStateOf(-1) }
    var verticalOffset by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()

    Column(
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        verticalOffset = offset.y
                        visibilities.keys.forEach { key -> visibilities[key]?.value = false }
                    },
                    onDragEnd = {
                        visibilities.keys.forEach { key -> visibilities[key]?.value = true }
                        selectedItemIndex = -1
                        verticalOffset = 0f
                    }
                ) { change, dragAmount ->
                    verticalOffset += dragAmount.y
                    val itemIndex = max(min((verticalOffset / 48.dp.toPx()).toInt(), keys.size - 1), 0)
                    if (itemIndex != selectedItemIndex) {
                        scope.launch {
                            listState.scrollToItem(
                                index = itemIndex + 1,
                                scrollOffset = -(viewModel.heightInPixels / 4)
                            )
                            visibilities.keys.forEach { key -> visibilities[key]?.value = false }
                            visibilities[keys[itemIndex]]?.value = true
                        }
                        selectedItemIndex = itemIndex
                    }
                    visibilities[keys[itemIndex]]?.value = true
                }
            }
    ) {
        keys.forEach { label ->
            Text(
                text = "$label",
                textAlign = TextAlign.Center,
                modifier = Modifier.size(48.dp)
            )
        }
    }
}
