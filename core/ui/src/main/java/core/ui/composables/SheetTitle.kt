package core.ui.composables

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.sp
import core.data.launchables.model.Launchable
import core.ui.R

private val titleFontSize = 28.sp

/** The icon-and-name header shown at the top of a launcher sheet. */
@Composable
fun SheetTitle(
    launchable: Launchable,
    modifier: Modifier = Modifier,
    onNameClick: (() -> Unit)? = null,
    onIconClick: (() -> Unit)? = null
) {
    val iconModifier: Modifier = if (onIconClick == null) {
        Modifier
    } else {
        Modifier.clickable(role = Role.Button, onClick = onIconClick)
    }
    val iconDescription: String = if (onIconClick == null) launchable.accessibilityName else stringResource(R.string.change_icon)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = sheetHorizontalPadding, vertical = sheetVerticalPadding)
    ) {
        LaunchableIcon(
            launchable = launchable,
            modifier = iconModifier,
            contentDescription = iconDescription
        )
        Text(
            text = launchable.name,
            fontSize = titleFontSize,
            modifier = if (onNameClick == null) Modifier else Modifier.clickable(onClick = onNameClick)
        )
    }
}
