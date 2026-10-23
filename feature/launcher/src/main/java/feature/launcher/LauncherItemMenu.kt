package feature.launcher

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.GripVertical
import compose.icons.fontawesomeicons.solid.InfoCircle
import core.data.apps.model.App
import core.data.launchables.model.Launchable
import core.ui.R as CoreUiR
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LauncherMenu(viewModel: LauncherViewModel) {
    val menuState: LauncherMenuState by viewModel.menuState.collectAsState()
    if (menuState is LauncherMenuState.Closed) {
        return
    }
    ModalBottomSheet(
        dragHandle = { LauncherSheetDragHandle() },
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
internal fun LauncherItemMenu(item: Launchable, viewModel: LauncherViewModel = koinViewModel()) {
    val context: Context = LocalContext.current

    CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.headlineMedium) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp, 10.dp)
        ) {
            LaunchableIcon(launchable = item)

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
            label = stringResource(R.string.edit_favorites),
            onClick = { viewModel.editFavorites() }
        )
        if (item is App) {
            LauncherMenuAction(
                imageVector = FontAwesomeIcons.Solid.InfoCircle,
                label = stringResource(R.string.app_info),
                onClick = { item.openAppInfo(context) }
            )
        }
    }
}

@Composable
internal fun LauncherItemEditName(item: Launchable, viewModel: LauncherViewModel = koinViewModel()) {
    var nameEdit: String by remember { mutableStateOf(item.name) }

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
                LaunchableIcon(launchable = item)
                Text(stringResource(R.string.rename_item, item.name))
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
                    Text(stringResource(CoreUiR.string.reset))
                }

                TextButton(
                    onClick = { viewModel.setItemName(item, nameEdit) }
                ) {
                    Text(stringResource(CoreUiR.string.done))
                }
            }
        }
    }
}
