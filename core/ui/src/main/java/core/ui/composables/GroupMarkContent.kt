package core.ui.composables

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.ShieldAlt
import core.ui.GroupMark
import core.ui.R

private val shieldOutlineWidth: Dp = 1.dp

internal val groupMarkShieldSize: Dp = 16.dp
internal val groupMarkLargeShieldSize: Dp = 28.dp

/** Draws [mark] for the Rail and the App List headers, with an outline unless [isOutlined] is false. */
@Composable
internal fun GroupMarkContent(
    mark: GroupMark,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.Center,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    textAlign: TextAlign? = null,
    fillColor: Color = MaterialTheme.colorScheme.onSurface,
    isOutlined: Boolean = true,
    shieldSize: Dp = groupMarkShieldSize
) {
    val style: TextStyle = LocalTextStyle.current
    when (mark) {
        is GroupMark.Label -> if (isOutlined) {
            OutlinedText(
                text = mark.text,
                modifier = modifier,
                contentAlignment = contentAlignment,
                fillColor = fillColor,
                fontSize = fontSize,
                fontWeight = fontWeight,
                lineHeight = lineHeight,
                textAlign = textAlign,
                style = style
            )
        } else {
            Text(
                text = mark.text,
                modifier = modifier,
                color = fillColor,
                fontSize = fontSize,
                fontWeight = fontWeight,
                lineHeight = lineHeight,
                textAlign = textAlign,
                style = style.copy(textMotion = TextMotion.Animated)
            )
        }

        GroupMark.PrivateSpaceShield -> Box(modifier = modifier, contentAlignment = contentAlignment) {
            val description: String = stringResource(R.string.private_space)
            if (isOutlined) {
                Icon(
                    imageVector = FontAwesomeIcons.Solid.ShieldAlt,
                    contentDescription = null,
                    modifier = Modifier.size(shieldSize + shieldOutlineWidth * 2f),
                    tint = MaterialTheme.colorScheme.surfaceContainerLowest
                )
            }
            Icon(
                imageVector = FontAwesomeIcons.Solid.ShieldAlt,
                contentDescription = description,
                modifier = Modifier.size(shieldSize),
                tint = fillColor
            )
        }
    }
}
