package core.data.icons

import android.content.ComponentCallbacks
import android.content.ComponentName
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.UserHandle
import core.data.apps.HomeApps
import core.data.apps.IAppRepository
import core.data.profiles.IProfileSource
import core.data.profiles.ProfileChange
import core.data.profiles.ProfileInfo
import core.data.profiles.ProfileKind
import core.data.profiles.isKnownNotPrivate
import core.data.runContained
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber

private const val ICON_FILE_EXTENSION: String = ".png"
private const val HASH_ALGORITHM: String = "SHA-256"
private const val NAME_SEPARATOR: String = "_"
private const val TEMPORARY_FILE_SUFFIX: String = ".tmp"

/**
 * `ActivityInfo.CONFIG_ASSETS_PATHS`, hidden from the SDK: `Configuration.diff` reports it when the
 * system's resources or overlays change, as a wallpaper colour or theme change does.
 */
private const val CONFIG_ASSETS_PATHS: Int = 0x80000000.toInt()

/** The configuration changes that can change a system icon's pixels without an App update. */
private const val ICON_CONFIGURATION_CHANGES: Int =
    ActivityInfo.CONFIG_UI_MODE or ActivityInfo.CONFIG_LOCALE or CONFIG_ASSETS_PATHS

/**
 * Stores one PNG per icon and request size under the icon's package directory, in a versioned
 * directory in Rail's cache directory. An App update gets a new file name, so the icon is never
 * read stale, and the earlier file goes when the new one is written. Package changes and removals
 * delete the package's directory, and the icons of packages the live App list no longer has go
 * after its first load.
 *
 * Writes are serialised per icon and size and publish a completed file with a rename, so a reader
 * never sees a partial PNG.
 */
class IHomeIconCacheImpl(
    context: Context,
    private val profileSource: IProfileSource,
    appRepository: IAppRepository,
    scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : IHomeIconCache {
    private val directory: File = homeIconsDirectory(context)
    private val _generations: MutableStateFlow<Map<String, Long>> = MutableStateFlow(emptyMap())
    override val generations: StateFlow<Map<String, Long>> = _generations.asStateFlow()
    private val claimedRevalidations: MutableSet<HomeIconCacheKey> = ConcurrentHashMap.newKeySet()
    private val writeMutexes: ConcurrentHashMap<String, Mutex> = ConcurrentHashMap()
    private val configurationGenerationCount: AtomicLong = AtomicLong()
    private val lastConfiguration: Configuration = Configuration(context.resources.configuration)

    init {
        context.applicationContext.registerComponentCallbacks(object : ComponentCallbacks {
            override fun onConfigurationChanged(newConfig: Configuration) {
                this@IHomeIconCacheImpl.onConfigurationChanged(newConfiguration = newConfig)
            }

            @Deprecated("Deprecated in Java")
            override fun onLowMemory() = Unit
        })
        scope.launch(dispatcher) {
            runContained(message = "Could not delete an earlier Home icon cache") {
                earlierHomeIconsDirectories(context).forEach { directory -> directory.deleteRecursively() }
            }
        }
        scope.launch(dispatcher) {
            runContained(message = "Could not evict the icons of uninstalled Apps") {
                evictUninstalledApps(appRepository = appRepository)
            }
        }
        scope.launch(dispatcher) {
            profileSource.changes.collect { change ->
                runContained(message = "Could not evict the Home icons of a changed package") {
                    change.evictedPackageNames().forEach { packageName -> evict(packageName = packageName) }
                }
            }
        }
    }

    override suspend fun read(key: HomeIconCacheKey): Bitmap? {
        if (!key.canRead()) {
            return null
        }
        return withContext(dispatcher) {
            val file: File = key.iconFile()
            if (!file.isFile) {
                return@withContext null
            }
            val bitmap: Bitmap? = runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
            if (bitmap == null) {
                Timber.w("Could not read a stored icon")
            }
            bitmap
        }
    }

    override suspend fun write(key: HomeIconCacheKey, bitmap: Bitmap) {
        if (!key.canStore()) {
            return
        }
        key.writeMutex().withLock {
            withContext(dispatcher) { storeIcon(key = key, bitmap = bitmap) }
        }
    }

    override suspend fun evict(packageName: String): Unit = withContext(dispatcher) {
        if (isSafeFileName(name = packageName)) {
            File(directory, packageName).deleteRecursively()
        }
    }

    override fun generation(identity: String): Long = generations.value[identity] ?: 0L

    override fun claimRevalidation(key: HomeIconCacheKey): Boolean = claimedRevalidations.add(key)

    override val configurationGeneration: Long get() = configurationGenerationCount.get()

    /** Lets every icon revalidate again and leave Coil's memory cache after a change that can alter system icons. */
    private fun onConfigurationChanged(newConfiguration: Configuration) {
        val changes: Int = synchronized(lastConfiguration) {
            lastConfiguration.diff(newConfiguration).also { lastConfiguration.setTo(newConfiguration) }
        }
        if (changes and ICON_CONFIGURATION_CHANGES == 0) {
            return
        }
        claimedRevalidations.clear()
        configurationGenerationCount.incrementAndGet()
    }

    private fun storeIcon(key: HomeIconCacheKey, bitmap: Bitmap) {
        val file: File = key.iconFile()
        val replacesStoredIcon: Boolean = file.isFile
        file.parentFile.mkdirs()
        val temporary: File = createTemporaryIconFile(file = file) ?: return
        if (!writeIconPng(file = temporary, bitmap = bitmap)) {
            temporary.delete()
            return
        }
        if (!temporary.renameTo(file)) {
            Timber.w("Could not replace a stored icon")
            temporary.delete()
            return
        }
        if (replacesStoredIcon) {
            _generations.update { current -> current + (key.identity to (current[key.identity] ?: 0L) + 1L) }
        }
        deleteEarlierVersions(key = key)
    }

    private fun createTemporaryIconFile(file: File): File? = runCatching {
        File.createTempFile(file.name, TEMPORARY_FILE_SUFFIX, file.parentFile)
    }.onFailure { exception -> Timber.w(exception, "Could not create a temporary icon file") }
        .getOrNull()

    /** Removes the versions of [key]'s icon that another last update time identifies. */
    private fun deleteEarlierVersions(key: HomeIconCacheKey) {
        val file: File = key.iconFile()
        val prefix: String = key.versionName()
        file.parentFile?.listFiles { candidate -> candidate.name.startsWith(prefix) && candidate != file }
            ?.forEach { candidate -> candidate.delete() }
    }

    /** Deletes the stored icons of packages the live App list does not have, once that list first loads. */
    private suspend fun evictUninstalledApps(appRepository: IAppRepository) {
        val livePackages: Set<String> = appRepository.homeApps
            .filterIsInstance<HomeApps.Live>()
            .first()
            .apps
            .mapTo(mutableSetOf()) { app -> app.packageName }
        withContext(dispatcher) {
            directory.list()?.filterNot { packageName -> packageName in livePackages }
                ?.forEach { packageName -> File(directory, packageName).deleteRecursively() }
        }
    }

    /** Whether [this] key's icon may be read: never one of a Private Space or an unknown Profile. */
    private fun HomeIconCacheKey.canRead(): Boolean {
        if (!isSafeFileName(name = packageName)) {
            return false
        }
        val user: UserHandle = profile ?: return true
        return profileSource.kindFor(user = user).isKnownNotPrivate
    }

    /**
     * Whether [this] key's icon may be written. An unbadged icon belongs to the personal Profile
     * only when that Profile lists its App, so a Private Space or unknown Profile's icon can never
     * reach the disk.
     */
    private fun HomeIconCacheKey.canStore(): Boolean {
        if (!isSafeFileName(name = packageName)) {
            return false
        }
        val user: UserHandle? = profile
        return if (user == null) {
            isListedInPersonalProfile(componentName = componentName)
        } else {
            profileSource.kindFor(user = user).isKnownNotPrivate
        }
    }

    /** Whether the personal Profile lists [componentName]'s App, read with one query of that Profile only. */
    private fun isListedInPersonalProfile(componentName: ComponentName): Boolean {
        val personalProfile: ProfileInfo = profileSource.profiles.value.firstOrNull { profile -> profile.kind == ProfileKind.Personal }
            ?: return false
        return profileSource.activities(packageName = componentName.packageName, user = personalProfile.user)
            .any { activity -> activity.componentName == componentName }
    }

    private fun HomeIconCacheKey.writeMutex(): Mutex = writeMutexes.computeIfAbsent(versionName()) { Mutex() }

    private fun HomeIconCacheKey.iconFile(): File =
        File(File(directory, packageName), "${versionName()}$lastUpdateTime$ICON_FILE_EXTENSION")

    /** The name every version of this key's icon shares, whatever its last update time. */
    private fun HomeIconCacheKey.versionName(): String = "${identityHash()}$NAME_SEPARATOR$densityDpi$NAME_SEPARATOR$sizePx$NAME_SEPARATOR"

    /** The stable identity of this key's icon across App updates. */
    private fun HomeIconCacheKey.identityHash(): String = MessageDigest.getInstance(HASH_ALGORITHM)
        .digest(identity.toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte) }
}

/** The packages whose icons a change evicts. */
private fun ProfileChange.evictedPackageNames(): List<String> = when (this) {
    is ProfileChange.PackageChanged -> listOf(packageName)

    is ProfileChange.PackageRemoved -> listOf(packageName)

    is ProfileChange.PackagesUnavailable -> packageNames

    is ProfileChange.PackageAdded,
    is ProfileChange.PackagesSuspended,
    is ProfileChange.ProfileAdded,
    is ProfileChange.ProfileRemoved,
    is ProfileChange.ProfileAvailable,
    is ProfileChange.ProfileUnavailable -> emptyList()
}
