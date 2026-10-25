package core.popups

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.InfoCircle
import compose.icons.fontawesomeicons.solid.PencilAlt
import core.data.apps.model.App
import core.ui.R as CoreUiR
import core.ui.composables.SheetDragHandle
import core.ui.composables.SheetMenuAction
import core.ui.composables.SheetTitle

/** The bottom sheet listing the open Pop-up's actions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PopupMenuSheet(viewModel: PopupViewModel) {
    val menuState: PopupMenuState by viewModel.menuState.collectAsState()
    val state: PopupMenuState.Open = menuState as? PopupMenuState.Open ?: return
    val context: Context = LocalContext.current
    ModalBottomSheet(
        dragHandle = { SheetDragHandle() },
        onDismissRequest = viewModel::closeMenu
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            SheetTitle(launchable = state.owner)
            CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.titleLarge) {
                state.items.forEach { item ->
                    when (item) {
                        PopupMenuItem.EditPopup -> SheetMenuAction(
                            imageVector = FontAwesomeIcons.Solid.PencilAlt,
                            label = stringResource(CoreUiR.string.edit_popup),
                            onClick = { viewModel.startEdit(state.owner) }
                        )

                        PopupMenuItem.AppInfo -> SheetMenuAction(
                            imageVector = FontAwesomeIcons.Solid.InfoCircle,
                            label = stringResource(CoreUiR.string.app_info),
                            onClick = {
                                (state.owner as? App)?.openAppInfo(context)
                                viewModel.closeMenu()
                                viewModel.close()
                            }
                        )
                    }
                }
            }
        }
    }
}
