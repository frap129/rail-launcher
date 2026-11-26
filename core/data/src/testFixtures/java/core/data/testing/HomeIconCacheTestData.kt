package core.data.testing

import android.content.Context
import android.content.res.Configuration
import core.data.apps.IAppRepository
import core.data.icons.IHomeIconCache
import core.data.icons.IHomeIconCacheImpl
import core.data.icons.homeIconsDirectory
import core.data.profiles.IProfileSource
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope

/** How long icon drawing tests wait for a revalidation or Home icon report. */
const val REPORT_TIMEOUT_MILLIS: Long = 5_000L

/** A Home icon cache whose background work runs on [this] test scope's scheduler. */
@OptIn(ExperimentalCoroutinesApi::class)
fun TestScope.createHomeIconCache(
    context: Context,
    profileSource: IProfileSource,
    appRepository: IAppRepository,
    dispatcher: CoroutineDispatcher = StandardTestDispatcher(testScheduler)
): IHomeIconCache = IHomeIconCacheImpl(
    context = context,
    profileSource = profileSource,
    appRepository = appRepository,
    scope = backgroundScope,
    dispatcher = dispatcher
)

/** [configuration] with its night mode flipped, a change that can alter system icons. */
fun nightModeToggled(configuration: Configuration): Configuration = Configuration(configuration).apply {
    val nightMode: Int = if (uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES) {
        Configuration.UI_MODE_NIGHT_NO
    } else {
        Configuration.UI_MODE_NIGHT_YES
    }
    uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightMode
}

/** [configuration] rotated to the other orientation, a layout-only change. */
fun orientationToggled(configuration: Configuration): Configuration = Configuration(configuration).apply {
    orientation = if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
        Configuration.ORIENTATION_PORTRAIT
    } else {
        Configuration.ORIENTATION_LANDSCAPE
    }
}

/** The directory that holds [context]'s Home icon cache. */
fun homeIconCacheDirectory(context: Context): File = homeIconsDirectory(context = context)

/** The icon files [context]'s Home icon cache stores for [packageName]. */
fun storedIconFiles(context: Context, packageName: String): List<File> =
    homeIconCacheDirectory(context = context).resolve(packageName).listFiles()?.toList().orEmpty()
