package core.util

import android.content.Context

fun screenHeightPx(context: Context) = context.resources.displayMetrics.heightPixels
fun screenHeightDp(context: Context) = screenHeightPx(context) / context.resources.displayMetrics.density
