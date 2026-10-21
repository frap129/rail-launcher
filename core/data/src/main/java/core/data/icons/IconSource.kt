package core.data.icons

import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import core.data.icons.model.Icon

internal sealed interface IconSource {
    val cacheKey: String

    fun loadDrawable(context: Context): Drawable?
}

private class RailIconSource(private val resId: Int) : IconSource {
    override val cacheKey: String = "rail:$resId"

    override fun loadDrawable(context: Context): Drawable? = ContextCompat.getDrawable(context, resId)
}

private class ActivityIconSource(private val componentName: ComponentName) : IconSource {
    override val cacheKey: String = "activity:${componentName.flattenToString()}"

    override fun loadDrawable(context: Context): Drawable? = context.packageManager.getActivityIcon(componentName)
}

private class ApplicationIconSource(private val packageName: String) : IconSource {
    override val cacheKey: String = "application:$packageName"

    override fun loadDrawable(context: Context): Drawable? = context.packageManager.getApplicationIcon(packageName)
}

private class PackIconSource(private val componentName: ComponentName, private val packPackageName: String, private val resId: Int) :
    IconSource {
    override val cacheKey: String = "pack:$packPackageName:${componentName.flattenToString()}:$resId"

    override fun loadDrawable(context: Context): Drawable? =
        context.packageManager.getResourcesForApplication(packPackageName).getDrawable(resId, null)
}

internal fun Icon.toSource(): IconSource = when (this) {
    is Icon.ActivityIcon -> ActivityIconSource(componentName)
    is Icon.ApplicationIcon -> ApplicationIconSource(packageName)
    is Icon.PackIcon -> PackIconSource(componentName, packPackageName, resId)
    is Icon.RailIcon -> RailIconSource(resId)
}
