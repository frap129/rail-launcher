package core.ui.composables

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithText
import org.robolectric.RuntimeEnvironment

/** The string resource [id] as the Robolectric application resolves it. */
fun getString(id: Int): String = RuntimeEnvironment.getApplication().getString(id)

/** Renders [content] and asserts the rows found by the string resources in [labels] paint the sheet container colour. */
fun ComposeContentTestRule.assertSheetRowsPaintSheetColor(content: @Composable () -> Unit, labels: List<Int>) {
    assertRowsPaintSheetColor(
        content = content,
        labels = { labels.map { label -> onNodeWithText(getString(label)) } }
    )
}
