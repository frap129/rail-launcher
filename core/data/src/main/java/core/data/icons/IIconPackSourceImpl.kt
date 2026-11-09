package core.data.icons

import android.content.Context
import core.util.DrawableCategory
import core.util.getDrawableCategoriesFromPack
import core.util.hasDrawable

class IIconPackSourceImpl(private val context: Context) : IIconPackSource {
    override fun drawableCategories(packPackageName: String): List<DrawableCategory> =
        getDrawableCategoriesFromPack(context, packPackageName)

    override fun hasDrawable(packPackageName: String, drawableName: String): Boolean = hasDrawable(context, drawableName, packPackageName)
}
