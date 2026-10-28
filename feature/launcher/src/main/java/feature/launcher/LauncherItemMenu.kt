package feature.launcher

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.FolderPlus
import compose.icons.fontawesomeicons.solid.GripVertical
import compose.icons.fontawesomeicons.solid.InfoCircle
import compose.icons.fontawesomeicons.solid.PencilAlt
import compose.icons.fontawesomeicons.solid.TrashAlt
import core.data.apps.model.App
import core.data.apps.model.isPrivateApp
import core.data.folders.Folder
import core.data.launchables.model.Launchable
import core.ui.R as CoreUiR
import core.ui.composables.LaunchableIcon
import core.ui.composables.SheetDragHandle
import core.ui.composables.SheetMenuAction
import core.ui.composables.SheetTitle
import core.ui.popups.IPopupEditor
import core.ui.popups.IPopupFolderActions
import core.util.MAX_LAUNCHABLE_NAME_LENGTH
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LauncherMenu(viewModel: LauncherViewModel, editor: IPopupEditor, folderActions: IPopupFolderActions) {
    val menuState: LauncherMenuState by viewModel.menuState.collectAsState()
    if (menuState is LauncherMenuState.Closed) {
        return
    }
    ModalBottomSheet(
        dragHandle = { SheetDragHandle() },
        onDismissRequest = { viewModel.closeItemMenu() }
    ) {
        when (val currentMenuState: LauncherMenuState = menuState) {
            is LauncherMenuState.Menu -> LauncherItemMenu(
                item = currentMenuState.item,
                viewModel = viewModel,
                editor = editor,
                folderActions = folderActions
            )

            is LauncherMenuState.Rename -> LauncherItemEditName(item = currentMenuState.item, viewModel = viewModel)

            LauncherMenuState.Closed -> {} // this state is never reached
        }
    }
}

@Composable
internal fun LauncherItemMenu(item: Launchable, viewModel: LauncherViewModel, editor: IPopupEditor, folderActions: IPopupFolderActions) {
    val context: Context = LocalContext.current

    CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.headlineMedium) {
        SheetTitle(
            launchable = item,
            onNameClick = { viewModel.openItemRename(item) }
        )
    }

    CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.titleLarge) {
        if (!item.isPrivateApp) {
            SheetMenuAction(
                imageVector = FontAwesomeIcons.Solid.GripVertical,
                label = stringResource(R.string.edit_favorites),
                onClick = { viewModel.editFavorites() }
            )
        }
        when (item) {
            is App -> AppMenuActions(
                item = item,
                context = context,
                viewModel = viewModel,
                editor = editor,
                folderActions = folderActions
            )

            is Folder -> FolderMenuActions(
                item = item,
                viewModel = viewModel,
                editor = editor,
                folderActions = folderActions
            )

            else -> AddToFolderAction(item = item, viewModel = viewModel, folderActions = folderActions)
        }
    }
}

@Composable
private fun AppMenuActions(
    item: App,
    context: Context,
    viewModel: LauncherViewModel,
    editor: IPopupEditor,
    folderActions: IPopupFolderActions
) {
    // Private Apps stay out of Favorites, Pop-ups and Folders while they are list-only.
    if (!item.isPrivateApp) {
        SheetMenuAction(
            imageVector = FontAwesomeIcons.Solid.PencilAlt,
            label = stringResource(CoreUiR.string.edit_popup),
            onClick = {
                viewModel.closeItemMenu()
                editor.startEdit(item)
            }
        )
        AddToFolderAction(item = item, viewModel = viewModel, folderActions = folderActions)
    }
    SheetMenuAction(
        imageVector = FontAwesomeIcons.Solid.InfoCircle,
        label = stringResource(CoreUiR.string.app_info),
        onClick = { item.openAppInfo(context) }
    )
}

@Composable
private fun FolderMenuActions(item: Folder, viewModel: LauncherViewModel, editor: IPopupEditor, folderActions: IPopupFolderActions) {
    SheetMenuAction(
        imageVector = FontAwesomeIcons.Solid.PencilAlt,
        label = stringResource(R.string.edit_folder),
        onClick = {
            viewModel.closeItemMenu()
            editor.startEdit(item)
        }
    )
    SheetMenuAction(
        imageVector = FontAwesomeIcons.Solid.TrashAlt,
        label = stringResource(CoreUiR.string.delete_folder),
        onClick = {
            viewModel.closeItemMenu()
            folderActions.requestDeleteFolder(item)
        }
    )
}

@Composable
private fun AddToFolderAction(item: Launchable, viewModel: LauncherViewModel, folderActions: IPopupFolderActions) {
    SheetMenuAction(
        imageVector = FontAwesomeIcons.Solid.FolderPlus,
        label = stringResource(R.string.add_to_folder),
        onClick = {
            viewModel.closeItemMenu()
            folderActions.startAddToFolder(item)
        }
    )
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
                onValueChange = { value -> nameEdit = value.take(MAX_LAUNCHABLE_NAME_LENGTH) },
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
