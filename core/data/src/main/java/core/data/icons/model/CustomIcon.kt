package core.data.icons.model

/** A user-chosen icon stored for a Launchable instead of a resource id. */
sealed interface CustomIcon {
    /** The stored string form, which is also this icon's cache key. */
    fun format(): String

    /** The Icon this Custom icon is drawn as. */
    fun toIcon(): Icon

    /** A drawable from [packPackageName], stored by [drawableName] so a pack update can keep it. */
    data class PackDrawable(val packPackageName: String, val drawableName: String) : CustomIcon {
        override fun format(): String = "$ENCODING_PREFIX$packPackageName$ENCODING_SEPARATOR$drawableName"

        override fun toIcon(): Icon.PackDrawableIcon = Icon.PackDrawableIcon(packPackageName = packPackageName, drawableName = drawableName)

        companion object {
            /** The stored and cache-key prefix shared by every pack-drawable form. */
            const val ENCODING_PREFIX: String = "pack-drawable:"
            const val ENCODING_SEPARATOR: String = ":"
        }
    }

    /** A gallery image stored as a PNG named [fileName] in Rail's private icons directory. */
    data class Image(val fileName: String) : CustomIcon {
        override fun format(): String = "$ENCODING_PREFIX$fileName"

        override fun toIcon(): Icon.ImageIcon = Icon.ImageIcon(fileName = fileName)

        companion object {
            /** The stored and cache-key prefix shared by every image form. */
            const val ENCODING_PREFIX: String = "image:"
        }
    }

    companion object {
        /** Reads [value] as a Custom icon, or null when it is not a stored Custom icon. */
        fun parse(value: String?): CustomIcon? = when {
            value == null -> null
            value.startsWith(Image.ENCODING_PREFIX) -> parseImage(value = value)
            value.startsWith(PackDrawable.ENCODING_PREFIX) -> parsePackDrawable(value = value)
            else -> null
        }

        private fun parseImage(value: String): Image? = value
            .removePrefix(Image.ENCODING_PREFIX)
            .takeIf { fileName -> fileName.isNotEmpty() }
            ?.let { fileName -> Image(fileName = fileName) }

        private fun parsePackDrawable(value: String): PackDrawable? {
            val prefix: String = PackDrawable.ENCODING_PREFIX
            val separatorIndex: Int = value.indexOf(
                char = PackDrawable.ENCODING_SEPARATOR[0],
                startIndex = prefix.length
            )
            if (separatorIndex <= prefix.length) {
                return null
            }
            return PackDrawable(
                packPackageName = value.substring(prefix.length, separatorIndex),
                drawableName = value.substring(separatorIndex + PackDrawable.ENCODING_SEPARATOR.length)
            )
        }
    }
}

/** The Custom icon form of this pack drawable. */
fun Icon.PackDrawableIcon.toCustomIcon(): CustomIcon.PackDrawable =
    CustomIcon.PackDrawable(packPackageName = packPackageName, drawableName = drawableName)

/** The Custom icon form of this image. */
fun Icon.ImageIcon.toCustomIcon(): CustomIcon.Image = CustomIcon.Image(fileName = fileName)
