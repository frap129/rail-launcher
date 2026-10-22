package core.data.room

import androidx.room.TypeConverter

/** Stores the Media Player's excluded packages as a newline-separated list. */
class ExcludedPackagesConverters {
    @TypeConverter
    fun fromExcludedPackagesToString(packages: Set<String>): String = packages.joinToString(separator = PACKAGE_SEPARATOR)

    @TypeConverter
    fun fromStringToExcludedPackages(value: String): Set<String> =
        value.split(PACKAGE_SEPARATOR).filter { packageName -> packageName.isNotBlank() }.toSet()
}

private const val PACKAGE_SEPARATOR: String = "\n"
