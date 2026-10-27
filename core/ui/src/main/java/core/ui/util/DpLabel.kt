package core.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import core.ui.R

/** Formats a dp value for the shared value label, used by both the top offset and the resize height. */
@Composable
fun formatDpLabel(dp: Int): String = stringResource(R.string.dp_value, dp)
