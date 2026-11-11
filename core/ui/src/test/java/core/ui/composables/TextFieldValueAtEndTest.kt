package core.ui.composables

import androidx.compose.ui.text.TextRange
import org.junit.Assert.assertEquals
import org.junit.Test

class TextFieldValueAtEndTest {
    @Test
    fun `puts the cursor after the text`() {
        val inputText: String = "gmail"

        val actualFieldValue = inputText.toTextFieldValueAtEnd()

        assertEquals(inputText, actualFieldValue.text)
        assertEquals(TextRange(inputText.length), actualFieldValue.selection)
    }
}
