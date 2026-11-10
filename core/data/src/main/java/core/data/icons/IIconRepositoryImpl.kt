package core.data.icons

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import core.data.apps.model.App
import core.data.folders.Folder
import core.data.icons.model.CustomIcon
import core.data.icons.model.Icon
import core.data.icons.model.IconPack
import core.data.icons.model.IconPackCategory
import core.data.icons.model.badgedFor
import core.data.icons.model.sortedCustomIconPacks
import core.data.launchables.model.Launchable
import core.data.profiles.IProfileSource
import core.data.profiles.ProfileChange
import core.data.profiles.ProfileInfo
import core.data.room.dao.CustomIconPackDao
import core.data.room.dao.PackIconDao
import core.data.shortcuts.ShortcutLaunchable
import core.util.drawableNameHasWord
import core.util.reemittingFlow
import core.util.wordsForSearch
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

internal const val ICON_PACK_THEME_ACTION: String = "com.novalauncher.THEME"
private const val MIN_LABEL_WORD_LENGTH: Int = 3
private const val MAX_LABEL_SUGGESTIONS: Int = 12

class IIconRepositoryImpl(
    private val context: Context,
    private val iconPackDao: CustomIconPackDao,
    private val iconDao: PackIconDao,
    private val iconPackSource: IIconPackSource,
    private val imageStore: IIconImageStore,
    private val profileSource: IProfileSource,
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : IIconRepository {
    private val packageManager: PackageManager = context.packageManager
    private val iconPackIntents = listOf(
        Intent(ICON_PACK_THEME_ACTION),
        Intent("org.adw.launcher.icons.ACTION_PICK_ICON"),
        Intent("com.dlto.atom.launcher.THEME"),
        Intent("app.lawnchair.icons.THEMED_ICON"),
        Intent("android.intent.action.MAIN").addCategory("com.anddoes.launcher.THEME")
    )

    private val _iconPacks: MutableSharedFlow<Set<IconPack>> = reemittingFlow<Set<IconPack>>(
        initialValue = setOf(IconPack.SystemIconPack)
    )
    override val iconPacks: Flow<Set<IconPack>> = _iconPacks.asSharedFlow()

    init {
        scope.launch(dispatcher) {
            updateIconPacks()
        }

        scope.launch(dispatcher) {
            profileSource.changes.collect { change ->
                val packageName: String = when (change) {
                    is ProfileChange.PackageAdded -> change.packageName
                    is ProfileChange.PackageRemoved -> change.packageName
                    is ProfileChange.PackageChanged -> change.packageName
                    else -> return@collect
                }
                if (isIconPackChange(packageName = packageName)) {
                    updateIconPacks(reemitUnchanged = true)
                }
            }
        }
    }

    // A removed icon pack is only in the cached list; an installed one is only in PackageManager.
    private fun isIconPackChange(packageName: String): Boolean = _iconPacks.replayCache.any { packs ->
        packs.any { pack -> pack is IconPack.CustomIconPack && pack.packageName == packageName }
    } ||
        iconPackIntents.any { intent ->
            packageManager.queryIntentActivities(intent, 0).any { info -> info.activityInfo.packageName == packageName }
        }

    /**
     * Re-reads the installed Icon packs. A pack update can change drawables without changing the
     * set, so [reemitUnchanged] re-emits an equal set; the startup read only emits a new one.
     */
    suspend fun updateIconPacks(reemitUnchanged: Boolean = false) {
        // Present cached packs for quick loading
        val cachedIconPacks = getIconPacksFromCache()
        emitIconPacks(iconPacks = cachedIconPacks, reemitUnchanged = false)

        // Query currently installed packs and update cache
        val installedIconPacks = getInstalledIconPacks()
        val iconsToCache: List<Icon.PackIcon> = installedIconPacks
            .filterIsInstance<IconPack.CustomIconPack>()
            .flatMap { pack -> iconPackSource.appIcons(packPackageName = pack.packageName) }
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
        emitIconPacks(iconPacks = installedIconPacks, reemitUnchanged = reemitUnchanged)
    }

    private fun emitIconPacks(iconPacks: Set<IconPack>, reemitUnchanged: Boolean) {
        if (reemitUnchanged || _iconPacks.replayCache.lastOrNull() != iconPacks) {
            _iconPacks.tryEmit(iconPacks)
        }
    }

    override suspend fun getIcon(packPackageName: String, componentName: ComponentName, profile: ProfileInfo): Icon {
        val baseIcon: Icon = iconDao.getIcon(componentName, packPackageName) ?: Icon.ActivityIcon(componentName)
        return baseIcon.badgedFor(profile = profile)
    }

    override suspend fun getCustomIcon(customIcon: CustomIcon, profile: ProfileInfo?): Icon? = withContext(dispatcher) {
        val isAvailable: Boolean = when (customIcon) {
            is CustomIcon.Image -> imageStore.hasImage(fileName = customIcon.fileName)

            is CustomIcon.PackDrawable -> iconPackSource.hasDrawable(
                packPackageName = customIcon.packPackageName,
                drawableName = customIcon.drawableName
            )
        }
        if (!isAvailable) {
            return@withContext null
        }
        val icon: Icon = customIcon.toIcon()
        if (profile == null) icon else icon.badgedFor(profile = profile)
    }

    override suspend fun getPackCategories(packPackageName: String): List<IconPackCategory> = withContext(dispatcher) {
        iconPackSource.drawableCategories(packPackageName = packPackageName).map { category ->
            IconPackCategory(
                name = category.name,
                icons = category.drawableNames.map { drawableName ->
                    Icon.PackDrawableIcon(packPackageName = packPackageName, drawableName = drawableName)
                }
            )
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getSuggestedIcons(launchable: Launchable): Flow<List<Icon>> = iconPacks
        .map { packs -> packs.sortedCustomIconPacks() }
        .flatMapLatest { packs -> suggestIcons(launchable = launchable, packs = packs) }
        .flowOn(dispatcher)

    private fun suggestIcons(launchable: Launchable, packs: List<IconPack.CustomIconPack>): Flow<List<Icon>> = when (launchable) {
        is App -> suggestAppIcons(app = launchable, packs = packs)
        is Folder, is ShortcutLaunchable -> suggestLabelIcons(label = launchable.name, packs = packs)
        else -> flowOf(emptyList())
    }

    private fun suggestAppIcons(app: App, packs: List<IconPack.CustomIconPack>): Flow<List<Icon>> = flow {
        val suggestions: MutableList<Icon> = mutableListOf(app.defaultIcon)
        emit(suggestions.toList())
        val iconsByPack: Map<String, Icon.PackIcon> = iconDao.getAllForComponent(componentName = app.componentName)
            .associateBy { icon -> icon.packPackageName }
        packs.forEach { pack ->
            val packIcon: Icon.PackIcon = iconsByPack[pack.packageName] ?: return@forEach
            val drawableName: String = iconPackSource.resolveDrawableName(packPackageName = pack.packageName, resId = packIcon.resId)
                ?: run {
                    Timber.w("Icon pack %s no longer provides %s", pack.packageName, app.componentName)
                    return@forEach
                }
            suggestions.add(Icon.PackDrawableIcon(packPackageName = pack.packageName, drawableName = drawableName))
            emit(suggestions.toList())
        }
    }

    private fun suggestLabelIcons(label: String, packs: List<IconPack.CustomIconPack>): Flow<List<Icon>> = flow {
        val words: List<String> = extractLabelWords(label = label)
        val suggestions: MutableList<Icon> = mutableListOf()
        emit(suggestions.toList())
        if (words.isEmpty()) {
            return@flow
        }
        for (pack in packs) {
            val matchingNames: List<String> = iconPackSource.drawableCategories(packPackageName = pack.packageName)
                .flatMap { category -> category.drawableNames }
                .distinct()
                .filter { drawableName -> words.any { word -> drawableName.drawableNameHasWord(term = word) } }
            for (drawableName in matchingNames) {
                if (suggestions.size >= MAX_LABEL_SUGGESTIONS) {
                    break
                }
                suggestions.add(Icon.PackDrawableIcon(packPackageName = pack.packageName, drawableName = drawableName))
            }
            emit(suggestions.toList())
            if (suggestions.size >= MAX_LABEL_SUGGESTIONS) {
                break
            }
        }
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

private fun extractLabelWords(label: String): List<String> =
    label.lowercase().wordsForSearch().filter { word -> word.length >= MIN_LABEL_WORD_LENGTH }
