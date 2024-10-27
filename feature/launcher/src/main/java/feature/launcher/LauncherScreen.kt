package feature.launcher

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.accompanist.drawablepainter.DrawablePainter
import core.apprepo.App
import core.ui.model.data.Destination
import org.koin.compose.koinInject

val launcherDestination = Destination(
    route = "launcher",
    content = { navController, _ ->
        LauncherScreen(navController)
    }
)

@Composable
fun LauncherScreen(navController: NavController, viewModel: LauncherViewModel = koinInject()) {
    val launcherItems = viewModel.launcherItems.collectAsState(emptyMap<Char, List<App>>())

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Top
    ) {
        items(launcherItems.value.keys.toList()) { key ->
            ItemGroup("$key", launcherItems.value[key]!!)
        }
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
