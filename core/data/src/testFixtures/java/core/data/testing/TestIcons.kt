package core.data.testing

import core.data.icons.model.Icon
import core.data.icons.model.IconPack

/** The package name tests give an uninstalled Icon pack. */
const val MISSING_ICON_PACK_PACKAGE: String = "com.example.missing-icon-pack"

/** The custom icon pack package name tests point at. */
const val TEST_ICON_PACK_PACKAGE: String = "com.example.iconpack"

/** The package name tests give a second installed Icon pack. */
const val SECOND_ICON_PACK_PACKAGE: String = "com.example.othericonpack"

/** The Rail icon resource id tests use for a Rail Launchable. */
const val TEST_RAIL_ICON_RES_ID: Int = 0

/** The drawable name tests use for a Custom icon. */
const val TEST_CUSTOM_ICON_DRAWABLE_NAME: String = "calendar"

/** The side length of the square gallery icon tests import. */
const val TEST_GALLERY_ICON_SIZE: Int = 192

/** The display density tests key and render icons at. */
const val TEST_DENSITY_DPI: Int = 320

/** The square size tests render a Home icon at. */
const val TEST_ICON_SIZE_PX: Int = 128

/** The centre pixel of a [TEST_ICON_SIZE_PX] Home icon, where tests read a rendered colour. */
const val TEST_ICON_CENTER_PX: Int = TEST_ICON_SIZE_PX / 2

/** The file name tests give an imported gallery icon. */
const val TEST_GALLERY_ICON_FILE_NAME: String = "gallery-icon.png"

/** The content URI tests give the Photo Picker's result. */
const val TEST_GALLERY_URI: String = "content://media/picker/0/gallery-image"

/** An installed Icon pack for tests. */
fun createCustomIconPack(name: String = "Custom Pack", packageName: String = TEST_ICON_PACK_PACKAGE): IconPack.CustomIconPack =
    IconPack.CustomIconPack(
        name = name,
        packageName = packageName,
        icon = Icon.ApplicationIcon(packageName = packageName)
    )
