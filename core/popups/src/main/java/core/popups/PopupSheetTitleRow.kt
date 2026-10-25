package core.popups

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private val titleHorizontalPadding = 20.dp
private val titleVerticalPadding = 10.dp

/** The title row with a trailing text action, shared by the Pop-up editor and picker. */
@Composable
internal fun PopupSheetTitleRow(title: String, actionLabel: String, onAction: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = titleHorizontalPadding, vertical = titleVerticalPadding)
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.headlineSmall
        )
        TextButton(onClick = onAction) {
            Text(actionLabel)
        }
    }
}
