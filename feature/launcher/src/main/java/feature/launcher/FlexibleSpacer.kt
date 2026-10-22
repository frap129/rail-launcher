package feature.launcher

/** The height of Home's flexible spacer in pixels, collapsing to zero when the content overflows. */
internal fun flexibleSpacerHeight(viewport: Int, itemSizes: List<Int>): Int = (viewport - itemSizes.sum()).coerceAtLeast(0)
