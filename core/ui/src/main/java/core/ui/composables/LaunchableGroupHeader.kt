package core.ui.composables

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import core.data.launchables.model.LaunchableGroup
import core.ui.toGroupMark

private val groupHeaderStartPadding = 20.dp
private val groupHeaderTopPadding = 24.dp
private val groupHeaderBottomPadding = 8.dp

/** The minimum height a group header or section title keeps, so their layouts line up. */
@Composable
private fun groupHeaderMinHeight(): Dp = with(LocalDensity.current) { MaterialTheme.typography.headlineMedium.lineHeight.toDp() }

/**
 * The App List header chrome every group mark and section title shares: the headline style,
 * full-width paddings and minimum height they line up on.
 */
@Composable
private fun LaunchableHeaderContainer(modifier: Modifier = Modifier, endPadding: Dp = 0.dp, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.headlineMedium) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(
                    start = groupHeaderStartPadding,
                    top = groupHeaderTopPadding,
                    end = endPadding,
                    bottom = groupHeaderBottomPadding
                )
                .heightIn(min = groupHeaderMinHeight()),
            contentAlignment = Alignment.CenterStart
        ) {
            content()
        }
    }
}

/** The outlined group mark shared by the App List and the Pop-up picker. */
@Composable
fun LaunchableGroupHeader(group: LaunchableGroup.Listed, modifier: Modifier = Modifier, endPadding: Dp = 0.dp) {
    LaunchableHeaderContainer(modifier = modifier, endPadding = endPadding) {
        GroupMarkContent(
            mark = group.toGroupMark(),
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Start,
            shieldSize = groupMarkLargeShieldSize
        )
    }
}

/** The title of a titled App List section, matching the group headers and announced as a heading. */
@Composable
fun LaunchableSectionHeader(title: String, modifier: Modifier = Modifier, endPadding: Dp = 0.dp) {
    LaunchableHeaderContainer(modifier = modifier, endPadding = endPadding) {
        Text(
            text = title,
            modifier = Modifier.semantics { heading() },
            fontWeight = FontWeight.SemiBold
        )
    }
}
