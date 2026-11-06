package core.data.icons

import android.content.Context
import core.util.getDrawableNamesFromPack
import core.util.hasDrawable

class IIconPackSourceImpl(private val context: Context) : IIconPackSource {
    override fun drawableNames(packPackageName: String): List<String> = getDrawableNamesFromPack(context, packPackageName)

    override fun hasDrawable(packPackageName: String, drawableName: String): Boolean = hasDrawable(context, drawableName, packPackageName)
}
