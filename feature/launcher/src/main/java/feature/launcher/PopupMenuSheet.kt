package feature.launcher

import android.content.Context
import android.os.Process
import android.os.UserHandle
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
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
import core.data.profiles.IProfileSource
import core.data.shortcuts.ShortcutLaunchable
import core.ui.R as CoreUiR
import core.ui.composables.SheetActionRow
import core.ui.composables.SheetDragHandle
import core.ui.composables.SheetTitle
import core.ui.composables.sheetContainerColor
import core.ui.composables.sheetRowContentInset
import core.ui.popups.IPopupEditor
import core.ui.popups.IPopupFolderActions
import core.util.openApplicationDetailsSettings
import feature.launcher.widgets.WidgetMenuTitle
import org.koin.compose.koinInject

/** The bottom sheet listing the open Pop-up's actions or an added item's actions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PopupMenuSheet(card: PopupCardViewModel, editor: IPopupEditor, folderActions: IPopupFolderActions) {
    val menuState: PopupMenuState by card.menuState.collectAsState()
    val state: PopupMenuState.Open = menuState as? PopupMenuState.Open ?: return
    val profileSource: IProfileSource = koinInject()
    ModalBottomSheet(
        dragHandle = { SheetDragHandle() },
        onDismissRequest = card::closeMenu,
        containerColor = sheetContainerColor
    ) {
        PopupMenuContent(state = state, profileSource = profileSource, card = card, editor = editor, folderActions = folderActions)
    }
}

/** The open Pop-up menu's title and action rows, on the sheet's container colour. */
@Composable
internal fun PopupMenuContent(
    state: PopupMenuState.Open,
    profileSource: IProfileSource,
    card: PopupCardViewModel,
    editor: IPopupEditor,
    folderActions: IPopupFolderActions
) {
    val context: Context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth()) {
        val widgetPage: PopupWidgetPage? = state.widgetPage
        if (widgetPage != null) {
            WidgetMenuTitle(icon = widgetPage.model.icon, label = widgetPage.model.label)
        } else {
            SheetTitle(launchable = state.target, horizontalPadding = sheetRowContentInset)
        }
        state.items.forEach { item ->
            when (item) {
                PopupMenuItem.EditPopup, PopupMenuItem.EditFolder -> SheetActionRow(
                    imageVector = FontAwesomeIcons.Solid.PencilAlt,
                    label = stringResource(
                        if (item == PopupMenuItem.EditPopup) CoreUiR.string.edit_popup else R.string.edit_folder
                    ),
                    onClick = { editor.startEdit(state.target) }
                )

                PopupMenuItem.DeleteFolder -> SheetActionRow(
                    imageVector = FontAwesomeIcons.Solid.TrashAlt,
                    label = stringResource(CoreUiR.string.delete_folder),
                    onClick = { folderActions.requestDeleteFolder(state.target) }
                )

                PopupMenuItem.AddWidget -> SheetActionRow(
                    imageVector = FontAwesomeIcons.Solid.PlusSquare,
                    label = stringResource(CoreUiR.string.add_widget),
                    onClick = card::startAddWidget
                )

                PopupMenuItem.AddToStack -> SheetActionRow(
                    imageVector = FontAwesomeIcons.Solid.PlusSquare,
                    label = stringResource(CoreUiR.string.add_next_to),
                    onClick = card::startAddWidget
                )

                PopupMenuItem.ResizeWidget -> SheetActionRow(
                    imageVector = FontAwesomeIcons.Solid.ArrowsAltV,
                    label = stringResource(CoreUiR.string.resize_widget),
                    onClick = card::resizeWidget
                )

                PopupMenuItem.ReconfigureWidget -> SheetActionRow(
                    imageVector = FontAwesomeIcons.Solid.Cog,
                    label = stringResource(CoreUiR.string.reconfigure_widget),
                    onClick = card::reconfigureWidget
                )

                PopupMenuItem.RemoveWidget -> SheetActionRow(
                    imageVector = FontAwesomeIcons.Solid.TrashAlt,
                    label = stringResource(CoreUiR.string.remove_widget),
                    onClick = card::removeWidget
                )

                PopupMenuItem.RemoveFromStack -> SheetActionRow(
                    imageVector = FontAwesomeIcons.Solid.TrashAlt,
                    label = stringResource(CoreUiR.string.remove_from_stack),
                    onClick = card::removeWidget
                )

                PopupMenuItem.RemoveFromPopup -> SheetActionRow(
                    imageVector = FontAwesomeIcons.Solid.TrashAlt,
                    label = stringResource(R.string.remove_from_popup),
                    onClick = card::removeMenuItem
                )

                PopupMenuItem.AppInfo -> appInfoFor(target = state.target, widgetPage = widgetPage)?.let { appInfo ->
                    SheetActionRow(
                        imageVector = FontAwesomeIcons.Solid.InfoCircle,
                        label = stringResource(CoreUiR.string.app_info),
                        onClick = {
                            context.openAppInfo(appInfo = appInfo, profileSource = profileSource)
                            card.closeMenu()
                            if (widgetPage == null) {
                                card.close()
                            }
                        }
                    )
                }
            }
        }
    }
}

/** Opens the app info [appInfo] points at. */
private fun Context.openAppInfo(appInfo: PopupAppInfo, profileSource: IProfileSource) {
    when (appInfo) {
        is PopupAppInfo.LaunchableAppInfo -> when (val launchable: Launchable = appInfo.launchable) {
            is App -> launchable.openAppInfo(this)
            is ShortcutLaunchable -> launchable.openAppInfo(this)
            else -> Unit
        }

        is PopupAppInfo.WidgetProviderAppInfo -> {
            val profile: UserHandle = profileSource.userForSerial(appInfo.profileSerial) ?: Process.myUserHandle()
            openApplicationDetailsSettings(appInfo.provider.packageName, profile)
        }
    }
}
