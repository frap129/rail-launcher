package core.ui.composables

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val groupHeaderStartPadding = 20.dp
private val groupHeaderTopPadding = 24.dp
private val groupHeaderBottomPadding = 8.dp

/** The outlined group label shared by the App List and the Pop-up picker. */
@Composable
fun LaunchableGroupHeader(label: String, modifier: Modifier = Modifier, endPadding: Dp = 0.dp) {
    CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.headlineMedium) {
        OutlinedText(
            text = label,
            textAlign = TextAlign.Start,
            fontWeight = FontWeight.SemiBold,
            modifier = modifier
                .fillMaxWidth()
                .padding(
                    start = groupHeaderStartPadding,
                    top = groupHeaderTopPadding,
                    end = endPadding,
                    bottom = groupHeaderBottomPadding
                )
        )
    }
}
