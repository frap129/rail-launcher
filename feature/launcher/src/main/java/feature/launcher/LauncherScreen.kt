package feature.launcher

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

    Box {
        LazyColumn(
            modifier = Modifier.fillMaxSize().align(Alignment.Center),
            state = launcherScrollState,
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.Top
        ) {
            items(launcherItems.value.keys.toList()) { key ->
                ItemGroup(
                    label = "$key",
                    items = launcherItems.value[key]!!
                )
            }
        }
        AlphaNumericScrollbar(
            modifier = Modifier.align(Alignment.CenterEnd),
            listState = launcherScrollState,
            keys = launcherItems.value.keys.toList()
        )
    }
}

@Composable
fun ItemGroup(label: String, items: List<App>) {
    val context = LocalContext.current

    Text(
        text = label,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(20.dp, 24.dp, 0.dp, 8.dp)
    )
    Column(
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Top
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
}

@Composable
fun AlphaNumericScrollbar(modifier: Modifier = Modifier, listState: LazyListState, keys: List<Char>) {
    var selectedItemIndex by remember { mutableIntStateOf(-1) }
    var verticalOffset by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()

    Column(
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                    }
                ) { change, dragAmount ->
                    verticalOffset += dragAmount.y
                    val itemIndex = max(min((verticalOffset / 48.dp.toPx()).toInt(), keys.size - 1), 0)
                    if (itemIndex != selectedItemIndex) {
                        scope.launch {
                            listState.scrollToItem(
                                index = itemIndex,
                                scrollOffset = -500
                            )
                        }
                    }
                    selectedItemIndex = itemIndex
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
