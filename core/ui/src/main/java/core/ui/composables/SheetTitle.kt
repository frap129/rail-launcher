package core.ui.composables

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.sp
import core.data.launchables.model.Launchable
import core.ui.R

private val titleFontSize = 28.sp

/** The test tag on a sheet title's drawn icon, used to measure where the title starts. */
const val SHEET_TITLE_ICON_TEST_TAG: String = "sheetTitleIcon"

/** A text-only sheet title, aligned with the sheet rows' content column. */
@Composable
fun SheetTextTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        modifier = modifier.padding(horizontal = sheetRowContentInset, vertical = sheetVerticalPadding),
        style = MaterialTheme.typography.headlineMedium
    )
}

/** The icon-and-name header shown at the top of a launcher sheet. */
@Composable
fun SheetTitle(
    launchable: Launchable,
    modifier: Modifier = Modifier,
    onNameClick: (() -> Unit)? = null,
    onIconClick: (() -> Unit)? = null
) {
    val iconClickModifier: Modifier = if (onIconClick == null) {
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
            .padding(horizontal = sheetRowContentInset, vertical = sheetVerticalPadding)
    ) {
        Box(modifier = Modifier.size(launchableIconSize).then(iconClickModifier), contentAlignment = Alignment.CenterStart) {
            LaunchableIconArtwork(
                launchable = launchable,
                modifier = Modifier.testTag(SHEET_TITLE_ICON_TEST_TAG),
                contentDescription = iconDescription
            )
        }
        Text(
            text = launchable.name,
            fontSize = titleFontSize,
            modifier = if (onNameClick == null) Modifier else Modifier.clickable(onClick = onNameClick)
        )
    }
}
