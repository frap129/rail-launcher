package core.ui.composables

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import core.data.launchables.model.LaunchableGroup
import core.ui.toGroupMark

private val groupHeaderStartPadding = 20.dp
private val groupHeaderTopPadding = 24.dp
private val groupHeaderBottomPadding = 8.dp

/** The outlined group mark shared by the App List and the Pop-up picker. */
@Composable
fun LaunchableGroupHeader(group: LaunchableGroup.Listed, modifier: Modifier = Modifier, endPadding: Dp = 0.dp) {
    val minHeight: Dp = with(LocalDensity.current) { MaterialTheme.typography.headlineMedium.lineHeight.toDp() }
    CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.headlineMedium) {
        GroupMarkContent(
            mark = group.toGroupMark(),
            modifier = modifier
                .fillMaxWidth()
                .padding(
                    start = groupHeaderStartPadding,
                    top = groupHeaderTopPadding,
                    end = endPadding,
                    bottom = groupHeaderBottomPadding
                )
                .heightIn(min = minHeight),
            contentAlignment = Alignment.CenterStart,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Start,
            shieldSize = groupMarkLargeShieldSize
        )
    }
}
