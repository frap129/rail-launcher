package core.data.icons

import android.content.Context
import core.data.icons.model.Icon
import core.util.DrawableCategory
import core.util.getDrawableCategoriesFromPack
import core.util.getDrawableName
import core.util.getIconsFromPack
import core.util.hasDrawable

class IIconPackSourceImpl(private val context: Context) : IIconPackSource {
    override fun drawableCategories(packPackageName: String): List<DrawableCategory> =
        getDrawableCategoriesFromPack(context, packPackageName)

    override fun hasDrawable(packPackageName: String, drawableName: String): Boolean = hasDrawable(context, drawableName, packPackageName)

    override fun resolveDrawableName(packPackageName: String, resId: Int): String? = getDrawableName(context, resId, packPackageName)

    override fun appIcons(packPackageName: String): Set<Icon.PackIcon> = getIconsFromPack(context, packPackageName)
        .mapTo(mutableSetOf()) { (componentName, packName, resId) ->
            Icon.PackIcon(componentName = componentName, packPackageName = packName, resId = resId)
        }
}
