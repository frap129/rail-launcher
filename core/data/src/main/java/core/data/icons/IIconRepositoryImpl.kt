package core.data.icons

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import core.data.icons.model.Icon
import core.data.icons.model.IconPack
import core.data.icons.model.badgedFor
import core.data.profiles.IProfileSource
import core.data.profiles.ProfileChange
import core.data.profiles.ProfileInfo
import core.data.room.dao.CustomIconPackDao
import core.data.room.dao.PackIconDao
import core.util.getIconsFromPack
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

class IIconRepositoryImpl(
    private val context: Context,
    private val iconPackDao: CustomIconPackDao,
    private val iconDao: PackIconDao,
    private val profileSource: IProfileSource,
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : IIconRepository {
    private val packageManager: PackageManager = context.packageManager
    private val iconPackIntents = listOf(
        Intent("com.novalauncher.THEME"),
        Intent("org.adw.launcher.icons.ACTION_PICK_ICON"),
        Intent("com.dlto.atom.launcher.THEME"),
        Intent("app.lawnchair.icons.THEMED_ICON"),
        Intent("android.intent.action.MAIN").addCategory("com.anddoes.launcher.THEME")
    )

    private val _iconPacks: MutableStateFlow<Set<IconPack>> = MutableStateFlow(setOf(IconPack.SystemIconPack))
    override val iconPacks: StateFlow<Set<IconPack>> = _iconPacks.asStateFlow()

    init {
        scope.launch(dispatcher) {
            updateIconPacks()
        }

        scope.launch(dispatcher) {
            profileSource.changes
                .filter { change ->
                    change is ProfileChange.PackageAdded ||
                        change is ProfileChange.PackageRemoved ||
                        change is ProfileChange.PackageChanged
                }
                .collect { updateIconPacks() }
        }
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

    override suspend fun getIcon(packPackageName: String, componentName: ComponentName, profile: ProfileInfo): Icon {
        val baseIcon: Icon = iconDao.getIcon(componentName, packPackageName) ?: Icon.ActivityIcon(componentName)
        return baseIcon.badgedFor(profile = profile)
    }

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
