package feature.launcher

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithText
import core.ui.composables.assertRowsPaintSheetColor

/** Renders [content] and asserts the rows found by the string resources in [labels] paint the sheet container colour. */
internal fun ComposeContentTestRule.assertSheetRowsPaintSheetColor(content: @Composable () -> Unit, labels: List<Int>) {
    assertRowsPaintSheetColor(
        content = content,
        labels = { labels.map { label -> onNodeWithText(getString(label)) } }
    )
}
