package core.data.icons

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import core.data.icons.model.Icon
import core.data.icons.model.IconPack
import core.data.room.dao.CustomIconPackDao
import core.data.room.dao.PackIconDao
import core.util.launchInBackground
import core.util.getIconsFromPack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class IconRepository(private val context: Context, private val iconPackDao: CustomIconPackDao, private val iconDao: PackIconDao) {
    private val packageManager: PackageManager = context.packageManager
    private val iconPackIntents = listOf(
        Intent("com.novalauncher.THEME"),
        Intent("org.adw.launcher.icons.ACTION_PICK_ICON"),
        Intent("com.dlto.atom.launcher.THEME"),
        Intent("app.lawnchair.icons.THEMED_ICON"),
        Intent("android.intent.action.MAIN").addCategory("com.anddoes.launcher.THEME")
    )

    private val _iconPacks: MutableStateFlow<Set<IconPack>> = MutableStateFlow(setOf(IconPack.SystemIconPack))
    val iconPacks: StateFlow<Set<IconPack>> = _iconPacks.asStateFlow()

    private val appChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            launchInBackground {
                updateIconPacks()
            }
        }
    }

    init {
        launchInBackground {
            updateIconPacks()
        }

        context.registerReceiver(
            appChangeReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addDataScheme("package")
            }
        )
    }

    suspend fun updateIconPacks() {
        // Present cached packs for quick loading
        val cachedIconPacks = getIconPacksFromCache()
        _iconPacks.value = cachedIconPacks

        // Query currently installed packs and update cache
        val installedIconPacks = getInstalledIconPacks()
        val iconsToCache = installedIconPacks.flatMap {
            if (it is IconPack.CustomIconPack) {
                getIconsFromPack(context, it.packageName).map { icon ->
                    Icon.PackIcon(
                        icon.first,
                        icon.second,
                        icon.third
                    )
                }
            } else {
                emptyList()
            }
        }
        val iconsToRemove = iconDao.getAll().filterNot { iconsToCache.contains(it) }
        val packsToCache = installedIconPacks
            .filterIsInstance<IconPack.CustomIconPack>()
        val packsToRemove = cachedIconPacks
            .filterIsInstance<IconPack.CustomIconPack>()
            .filterNot { installedIconPacks.contains(it) }
        iconsToRemove.forEach { iconDao.delete(it) }
        iconsToCache.forEach { iconDao.insert(it) }
        packsToRemove.forEach { iconPackDao.delete(it) }
        packsToCache.forEach { iconPackDao.insert(it) }
        _iconPacks.value = installedIconPacks
    }

    suspend fun getIcon(packPackageName: String, componentName: ComponentName) = iconDao.getIcon(componentName, packPackageName)

    private suspend fun getIconPacksFromCache(): Set<IconPack> = mutableSetOf<IconPack>(IconPack.SystemIconPack).apply {
        iconPackDao.getAll().forEach { pack ->
            add(
                IconPack.CustomIconPack(
                    pack.name,
                    pack.packageName,
                    pack.icon
                )
            )
        }
    }

    private fun getInstalledIconPacks(): Set<IconPack> = iconPackIntents
        .flatMap { packageManager.queryIntentActivities(it, 0) }
        .mapTo(mutableSetOf<IconPack>()) { info ->
            IconPack.CustomIconPack(
                info.loadLabel(packageManager).toString(),
                info.activityInfo.packageName,
                Icon.ApplicationIcon(info.activityInfo.packageName)
            )
        }.apply {
            add(IconPack.SystemIconPack)
        }
}
