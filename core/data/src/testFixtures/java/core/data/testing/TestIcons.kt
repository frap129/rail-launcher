package core.data.testing

import core.data.icons.model.Icon
import core.data.icons.model.IconPack

/** The custom icon pack package name tests point at. */
const val TEST_ICON_PACK_PACKAGE: String = "com.example.iconpack"

/** The Rail icon resource id tests use for a Rail Launchable. */
const val TEST_RAIL_ICON_RES_ID: Int = 0

/** The drawable name tests use for a Custom icon. */
const val TEST_CUSTOM_ICON_DRAWABLE_NAME: String = "calendar"

/** The side length of the square gallery icon tests import. */
const val TEST_GALLERY_ICON_SIZE: Int = 192

/** The file name tests give an imported gallery icon. */
const val TEST_GALLERY_ICON_FILE_NAME: String = "gallery-icon.png"

/** An installed Icon pack for tests. */
fun createCustomIconPack(name: String = "Custom Pack", packageName: String = TEST_ICON_PACK_PACKAGE): IconPack.CustomIconPack =
    IconPack.CustomIconPack(
        name = name,
        packageName = packageName,
        icon = Icon.ApplicationIcon(packageName = packageName)
    )
