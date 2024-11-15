package core.data.icons

import android.annotation.SuppressLint
import android.graphics.drawable.Drawable
import coil3.map.Mapper
import coil3.request.Options
import core.data.icons.model.Icon

class IconMapper : Mapper<Icon, Drawable> {
    @SuppressLint("UseCompatLoadingForDrawables")
    override fun map(data: Icon, options: Options): Drawable? = when (data) {
        is Icon.ActivityIcon -> options.context.packageManager.getActivityIcon(data.componentName)
        is Icon.ApplicationIcon -> options.context.packageManager.getApplicationIcon(data.packageName)
        is Icon.PackIcon -> options.context.packageManager.getResourcesForApplication(data.packPackageName).getDrawable(data.resId, null)
    }
}
