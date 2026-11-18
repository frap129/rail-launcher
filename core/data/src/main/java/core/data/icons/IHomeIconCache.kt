package core.data.icons

import android.graphics.Bitmap
import kotlinx.coroutines.flow.StateFlow

/**
 * The rendered App icons Rail keeps on disk for every icon it draws, keyed by [HomeIconCacheKey],
 * so an icon draws without loading its App's resources. The icons live in Rail's cache directory,
 * which Android cloud backup and device transfer never carry.
 */
interface IHomeIconCache {
    /** The stored bitmap for [key], or null when none is stored or it cannot be read. */
    suspend fun read(key: HomeIconCacheKey): Bitmap?

    /** Stores [bitmap] as [key]'s icon, replacing the icon's earlier versions. */
    suspend fun write(key: HomeIconCacheKey, bitmap: Bitmap)

    /** Removes every stored icon of [packageName]. */
    suspend fun evict(packageName: String)

    /**
     * How often a revalidation replaced each icon's stored bitmap in this process, by icon
     * identity. A change means every size of that icon must load its bitmap again, so Coil's
     * memory key and the icons on screen follow this one signal.
     */
    val generations: StateFlow<Map<String, Long>>

    /** The revalidation generation of [identity], for callers that only need its current value. */
    fun generation(identity: String): Long

    /**
     * Claims [key]'s one background revalidation until the next configuration change that can alter
     * system icons, returning whether it was unclaimed.
     */
    fun claimRevalidation(key: HomeIconCacheKey): Boolean

    /**
     * How many configuration changes that can alter system icons (night mode, locale, or the
     * system's resources and overlays) this process has seen. Each one releases every revalidation
     * claim and joins Coil's memory key, so every icon drawn after it is read and revalidated again.
     */
    val configurationGeneration: Long
}
