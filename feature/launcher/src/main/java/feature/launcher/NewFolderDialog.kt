package feature.launcher

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import core.data.folders.Folder
import core.ui.R as CoreUiR

/** Asks for a new Folder's name, prefilled and selected so typing replaces it. */
@Composable
internal fun NewFolderDialog(viewModel: LauncherViewModel) {
    var name: TextFieldValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = Folder.DEFAULT_NAME,
                selection = TextRange(0, Folder.DEFAULT_NAME.length)
            )
        )
    }
    val focusRequester: FocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    AlertDialog(
        onDismissRequest = viewModel::closeNewFolderDialog,
        title = { Text(stringResource(R.string.new_folder)) },
        text = {
            OutlinedTextField(
                modifier = Modifier.focusRequester(focusRequester),
                value = name,
                onValueChange = { value ->
                    val text: String = value.text.take(MAX_LAUNCHABLE_NAME_LENGTH)
                    name = if (text == value.text) value else value.copy(text = text, selection = TextRange(text.length))
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { viewModel.createFolder(name.text) })
            )
        },
        confirmButton = {
            TextButton(onClick = { viewModel.createFolder(name.text) }) {
                Text(stringResource(R.string.create))
            }
        },
        dismissButton = {
            TextButton(onClick = viewModel::closeNewFolderDialog) {
                Text(stringResource(CoreUiR.string.cancel))
            }
        }
    )
}
