package core.popups

import android.content.Context
import android.os.Process
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
import compose.icons.fontawesomeicons.solid.ArrowsAltV
import compose.icons.fontawesomeicons.solid.Cog
import compose.icons.fontawesomeicons.solid.InfoCircle
import compose.icons.fontawesomeicons.solid.PencilAlt
import compose.icons.fontawesomeicons.solid.PlusSquare
import compose.icons.fontawesomeicons.solid.TrashAlt
import core.data.apps.model.App
import core.data.launchables.model.Launchable
import core.data.shortcuts.ShortcutLaunchable
import core.ui.R as CoreUiR
import core.ui.composables.SheetDragHandle
import core.ui.composables.SheetMenuAction
import core.ui.composables.SheetTitle
import core.util.openApplicationDetailsSettings
import core.util.userForSerial
import core.widgets.WidgetMenuTitle

/** The bottom sheet listing the open Pop-up's actions or an added item's actions. */
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
            if (state.widget != null) {
                WidgetMenuTitle(icon = state.widget.model.icon, label = state.widget.model.label)
            } else {
                SheetTitle(launchable = state.target)
            }
            CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.titleLarge) {
                state.items.forEach { item ->
                    when (item) {
                        PopupMenuItem.EditPopup, PopupMenuItem.EditFolder -> SheetMenuAction(
                            imageVector = FontAwesomeIcons.Solid.PencilAlt,
                            label = stringResource(
                                if (item == PopupMenuItem.EditPopup) CoreUiR.string.edit_popup else R.string.edit_folder
                            ),
                            onClick = { viewModel.startEdit(state.target) }
                        )

                        PopupMenuItem.DeleteFolder -> SheetMenuAction(
                            imageVector = FontAwesomeIcons.Solid.TrashAlt,
                            label = stringResource(R.string.delete_folder),
                            onClick = viewModel::requestDeleteFolder
                        )

                        PopupMenuItem.AddWidget -> SheetMenuAction(
                            imageVector = FontAwesomeIcons.Solid.PlusSquare,
                            label = stringResource(CoreUiR.string.add_widget),
                            onClick = viewModel::startAddWidget
                        )

                        PopupMenuItem.ResizeWidget -> SheetMenuAction(
                            imageVector = FontAwesomeIcons.Solid.ArrowsAltV,
                            label = stringResource(CoreUiR.string.resize_widget),
                            onClick = viewModel::resizeWidget
                        )

                        PopupMenuItem.ReconfigureWidget -> SheetMenuAction(
                            imageVector = FontAwesomeIcons.Solid.Cog,
                            label = stringResource(CoreUiR.string.reconfigure_widget),
                            onClick = viewModel::reconfigureWidget
                        )

                        PopupMenuItem.RemoveWidget -> SheetMenuAction(
                            imageVector = FontAwesomeIcons.Solid.TrashAlt,
                            label = stringResource(CoreUiR.string.remove_widget),
                            onClick = viewModel::removeWidget
                        )

                        PopupMenuItem.RemoveFromPopup -> SheetMenuAction(
                            imageVector = FontAwesomeIcons.Solid.TrashAlt,
                            label = stringResource(R.string.remove_from_popup),
                            onClick = { viewModel.removeItem(state.target) }
                        )

                        PopupMenuItem.AppInfo -> SheetMenuAction(
                            imageVector = FontAwesomeIcons.Solid.InfoCircle,
                            label = stringResource(CoreUiR.string.app_info),
                            onClick = {
                                val widget: PopupWidgetState? = state.widget
                                if (widget != null) {
                                    val profile = context.userForSerial(widget.profileSerial) ?: Process.myUserHandle()
                                    context.openApplicationDetailsSettings(widget.provider.packageName, profile)
                                    viewModel.closeMenu()
                                } else {
                                    when (val target: Launchable = state.target) {
                                        is App -> target.openAppInfo(context)
                                        is ShortcutLaunchable -> context.openApplicationDetailsSettings(target.packageName, target.profile)
                                        else -> Unit
                                    }
                                    viewModel.closeMenu()
                                    viewModel.close()
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
