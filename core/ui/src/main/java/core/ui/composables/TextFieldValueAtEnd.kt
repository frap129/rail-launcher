package core.ui.composables

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/** This text as a field value whose cursor sits after its last character. */
fun String.toTextFieldValueAtEnd(): TextFieldValue = TextFieldValue(text = this, selection = TextRange(length))
