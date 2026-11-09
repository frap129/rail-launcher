package core.data.icons.model

/** A user-chosen icon stored for a Launchable instead of a resource id. */
sealed interface CustomIcon {
    /** A drawable from [packPackageName], stored by [drawableName] so a pack update can keep it. */
    data class PackDrawable(val packPackageName: String, val drawableName: String) : CustomIcon {
        companion object {
            /** The stored and cache-key prefix shared by every pack-drawable form. */
            const val ENCODING_PREFIX: String = "pack-drawable:"
            const val ENCODING_SEPARATOR: String = ":"
        }
    }

    /** A gallery image stored as a PNG named [fileName] in Rail's private icons directory. */
    data class Image(val fileName: String) : CustomIcon {
        companion object {
            /** The stored and cache-key prefix shared by every image form. */
            const val ENCODING_PREFIX: String = "image:"
        }
    }
}
