package core.util

import android.content.Context
import kotlin.math.roundToInt

fun Int.toPx(context: Context) = (this * context.resources.displayMetrics.density).roundToInt()
fun Int.toDp(context: Context) = this / context.resources.displayMetrics.density
fun screenHeightPx(context: Context) = context.resources.displayMetrics.heightPixels
fun screenHeightDp(context: Context) = screenHeightPx(context).toDp(context)
