package feature.launcher.widgets

import android.graphics.drawable.Drawable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import core.data.widgets.WidgetProviderInfo
import core.data.widgets.WidgetSize
import core.ui.R as CoreUiR
import feature.launcher.R

/** Rail's App Widget picker, opened from any "Add widget" entry point. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppWidgetPickerSheet(
    state: AddWidgetFlowState.Picking,
    onQueryChange: (String) -> Unit,
    onPick: (WidgetProviderInfo) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(modifier = Modifier.fillMaxHeight()) {
            Text(
                text = stringResource(CoreUiR.string.add_widget),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                style = MaterialTheme.typography.headlineMedium
            )
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                placeholder = { Text(text = stringResource(R.string.search_widgets)) },
                singleLine = true
            )
            if (state.groups.isEmpty()) {
                Text(
                    text = stringResource(R.string.no_widgets_found),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
                    style = MaterialTheme.typography.bodyLarge
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxHeight()) {
                    state.groups.forEach { group ->
                        item(key = "group:${group.appPackageName}:${group.widgets.first().profileSerial}") {
                            PickerGroupHeader(group = group)
                        }
                        items(items = group.widgets, key = { widget -> "${widget.provider.key}:${widget.profileSerial}" }) { widget ->
                            PickerWidgetRow(widget = widget, onPick = onPick)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerGroupHeader(group: PickerGroup) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = group.appIcon,
            contentDescription = null,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = group.appLabel,
            modifier = Modifier.padding(start = 10.dp),
            style = MaterialTheme.typography.titleMedium
        )
        if (group.isWorkProfile) {
            Text(
                text = stringResource(R.string.work),
                modifier = Modifier.padding(start = 8.dp),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun PickerWidgetRow(widget: WidgetProviderInfo, onPick: (WidgetProviderInfo) -> Unit) {
    val context = LocalContext.current
    val preview: Drawable? by produceState<Drawable?>(initialValue = null, widget) {
        value = AppWidgetViews.loadPreview(context = context, info = widget)
    }
    val workBadge: Drawable? by produceState<Drawable?>(initialValue = null, widget) {
        value = if (widget.isWorkProfile) {
            AppWidgetViews.loadWorkBadge(context = context, profileSerial = widget.profileSerial)
        } else {
            null
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPick(widget) }
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(56.dp)) {
            AsyncImage(
                model = preview ?: widget.appIcon,
                contentDescription = stringResource(R.string.widget_preview, widget.label),
                modifier = Modifier.size(56.dp)
            )
            if (workBadge != null) {
                AsyncImage(
                    model = workBadge,
                    contentDescription = null,
                    modifier = Modifier.align(Alignment.BottomEnd).size(18.dp)
                )
            }
        }
        Column(modifier = Modifier.padding(start = 16.dp)) {
            Text(
                text = widget.label,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            widget.description?.let { description ->
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = widget.defaultSizeLabel(), style = MaterialTheme.typography.bodySmall)
                if (widget.isWorkProfile) {
                    Text(
                        text = stringResource(R.string.work),
                        modifier = Modifier.padding(start = 8.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun WidgetProviderInfo.defaultSizeLabel(): String = widgetSizeLabel(size = cellSize ?: defaultSize, isCellSize = cellSize != null)

@Composable
private fun widgetSizeLabel(size: WidgetSize, isCellSize: Boolean): String = if (isCellSize) {
    stringResource(R.string.widget_size_cells, size.widthDp, size.heightDp)
} else {
    stringResource(R.string.widget_size_dp, size.widthDp, size.heightDp)
}
