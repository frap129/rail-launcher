package feature.launcher

import android.view.KeyCharacterMap
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEvent as ComposeKeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import core.ui.R as CoreUiR
import core.ui.composables.SheetDragHandle
import core.ui.composables.SheetRow
import core.ui.composables.SheetTextTitle
import core.ui.composables.formatKeyCombination
import core.ui.composables.rowFocusIndicator
import core.ui.model.data.KeyboardShortcut

internal const val KEYBOARD_SHORTCUTS_HELP_LIST_TEST_TAG: String = "keyboardShortcutsHelpList"

/** The Keyboard shortcuts help sheet, listing the same shortcuts as the system helper. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun KeyboardShortcutsHelpSheet(onDismiss: () -> Unit) {
    val sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val titleFocusRequester: FocusRequester = remember { FocusRequester() }
    val keyCharacterMap: KeyCharacterMap = remember { KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD) }
    BackHandler(onBack = onDismiss)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { SheetDragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .focusGroup()
                .onPreviewKeyEvent { event: ComposeKeyEvent ->
                    if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ESCAPE) {
                        onDismiss()
                        true
                    } else {
                        false
                    }
                }
        ) {
            SheetTextTitle(
                title = stringResource(CoreUiR.string.keyboard_shortcuts_help),
                modifier = Modifier
                    .focusRequester(titleFocusRequester)
                    .focusable()
                    .rowFocusIndicator()
                    .semantics { heading() }
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(KEYBOARD_SHORTCUTS_HELP_LIST_TEST_TAG)
            ) {
                items(
                    items = KeyboardShortcut.entries,
                    key = { shortcut: KeyboardShortcut -> shortcut.name }
                ) { shortcut: KeyboardShortcut ->
                    KeyboardShortcutHelpRow(shortcut = shortcut, keyCharacterMap = keyCharacterMap)
                }
            }
        }
    }
    LaunchedEffect(titleFocusRequester) { titleFocusRequester.requestFocus() }
}

@Composable
private fun KeyboardShortcutHelpRow(shortcut: KeyboardShortcut, keyCharacterMap: KeyCharacterMap) {
    val actionLabel: String = stringResource(shortcut.label, *shortcut.labelArguments.toTypedArray())
    val keyCombination: String = formatKeyCombination(shortcut = shortcut, keyCharacterMap = keyCharacterMap)
    val announcement: String = stringResource(R.string.keyboard_shortcut_announcement, actionLabel, keyCombination)
    SheetRow(
        modifier = Modifier.contentDescriptionSemantics(description = announcement, mergeDescendants = true),
        headlineContent = { Text(text = actionLabel) },
        supportingContent = { Text(text = keyCombination) }
    )
}
