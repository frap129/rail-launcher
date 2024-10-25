package feature.launcher

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.accompanist.drawablepainter.DrawablePainter
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
    val context = LocalContext.current
    val apps = viewModel.apps.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Top
    ) {
        items(apps.value) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start,
                modifier = Modifier.clickable {
                    it.launch(context)
                }
            ) {
                Image(
                    modifier = Modifier.size(64.dp).padding(8.dp),
                    painter = DrawablePainter(it.getIcon(context)),
                    contentDescription = it.label
                )
                Text(it.label)
            }
        }
    }
}
