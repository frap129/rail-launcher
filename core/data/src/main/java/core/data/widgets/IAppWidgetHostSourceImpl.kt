package core.data.widgets

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.os.Bundle
import android.os.Process
import android.os.UserHandle
import core.data.icons.IIconRepository
import core.data.icons.model.Icon
import core.data.icons.model.SYSTEM_ICON_PACK_NAME
import core.data.prefs.IPreferencesRepository
import core.data.profiles.IProfileSource
import core.data.profiles.ProfileChange
import core.data.profiles.ProfileInfo
import core.data.profiles.ProfileKind
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import timber.log.Timber

class IAppWidgetHostSourceImpl(
    private val context: Context,
    private val appWidgetHost: AppWidgetHost,
    private val iconRepository: IIconRepository,
    private val preferencesRepository: IPreferencesRepository,
    private val callbacks: AppWidgetHostCallbacks,
    private val profileSource: IProfileSource,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : IAppWidgetHostSource {
    private val appWidgetManager: AppWidgetManager = AppWidgetManager.getInstance(context)
    private val launcherApps: LauncherApps = context.getSystemService(LauncherApps::class.java)

    @Volatile
    private var cachedProviders: List<WidgetProviderInfo> = emptyList()

    private val replacingPackages: MutableSet<String> = ConcurrentHashMap.newKeySet()

    override val providerChanges: Flow<Unit> = merge(
        callbacks.providerChanges,
        profileSource.changes.map { change ->
            trackReplacingPackage(change = change)
            Unit
        }
    ).conflate()

    override val providers: Flow<List<WidgetProviderInfo>> = providerChanges
        .onStart { emit(Unit) }
        .map {
            val loaded: List<WidgetProviderInfo> = loadProviders()
            cachedProviders = loaded
            loaded
        }
        .flowOn(dispatcher)

    override fun allocateId(): AppWidgetId = AppWidgetId(appWidgetHost.allocateAppWidgetId())

    override fun deleteId(id: AppWidgetId) {
        try {
            appWidgetHost.deleteAppWidgetId(id.value)
        } catch (exception: IllegalArgumentException) {
            Timber.w(exception, "Could not delete App Widget %d", id.value)
        }
    }

    override fun bindIfAllowed(request: BindRequest): Boolean {
        val user: UserHandle = profileSource.userForSerial(request.profileSerial) ?: return false
        val provider: ComponentName = ComponentName(request.provider.packageName, request.provider.className)
        return appWidgetManager.bindAppWidgetIdIfAllowed(request.id.value, user, provider, null)
    }

    override fun boundIds(): Set<AppWidgetId> = appWidgetHost.appWidgetIds.mapTo(mutableSetOf()) { id -> AppWidgetId(id) }

    override fun availability(id: AppWidgetId, provider: WidgetProvider, profileSerial: Long): WidgetAvailability {
        val user: UserHandle = profileSource.userForSerial(profileSerial) ?: return WidgetAvailability.Gone
        if (provider.packageName in replacingPackages) {
            return WidgetAvailability.Updating
        }
        val appInfo: ApplicationInfo = runCatching {
            launcherApps.getApplicationInfo(provider.packageName, 0, user)
        }.getOrNull() ?: return WidgetAvailability.Gone
        if (profileSource.profileFor(user = user)?.isQuiet == true) {
            return WidgetAvailability.ProfilePaused
        }
        if (!appInfo.enabled || appInfo.flags and ApplicationInfo.FLAG_SUSPENDED != 0) {
            return WidgetAvailability.Disabled
        }
        return if (appWidgetManager.getAppWidgetInfo(id.value) == null) {
            WidgetAvailability.Unknown
        } else {
            WidgetAvailability.Available
        }
    }

    override fun providerInfo(id: AppWidgetId): WidgetProviderInfo? {
        val info: AppWidgetProviderInfo = appWidgetManager.getAppWidgetInfo(id.value) ?: return null
        val user: UserHandle = ownerOf(info) ?: Process.myUserHandle()
        val provider: WidgetProvider = WidgetProvider(packageName = info.provider.packageName, className = info.provider.className)
        val profileSerial: Long = profileSource.serialForUser(user) ?: return null
        val cached: WidgetProviderInfo? = cachedProviders.firstOrNull { cached ->
            cached.provider == provider && cached.profileSerial == profileSerial
        }
        return info.toWidgetProviderInfo(
            user = user,
            profileSerial = profileSerial,
            profileKind = profileSource.profileFor(user = user)?.kind ?: ProfileKind.Other,
            appIcon = cached?.appIcon ?: Icon.ApplicationIcon(info.provider.packageName)
        )
    }

    override fun profileSerial(profile: UserHandle): Long? = profileSource.serialForUser(profile)

    override fun startListening() {
        appWidgetHost.startListening()
    }

    override fun stopListening() {
        appWidgetHost.stopListening()
    }

    override fun startConfiguring(activity: Activity, id: AppWidgetId, requestCode: Int) {
        appWidgetHost.startAppWidgetConfigureActivityForResult(activity, id.value, 0, requestCode, Bundle.EMPTY)
    }

    private suspend fun loadProviders(): List<WidgetProviderInfo> {
        val iconPack: String = preferencesRepository.settings.first().iconPack ?: SYSTEM_ICON_PACK_NAME
        return profileSource.profiles.value.filter { profile: ProfileInfo ->
            profile.kind != ProfileKind.Private
        }.flatMap { profile: ProfileInfo ->
            runCatching { appWidgetManager.getInstalledProvidersForProfile(profile.user) }
                .onFailure { exception -> Timber.w(exception, "Could not load App Widget providers for %s", profile.user) }
                .getOrDefault(emptyList())
                .filter { info ->
                    isPickerProvider(
                        isOwnPackage = info.provider.packageName == context.packageName,
                        isHomeScreenCategory = info.widgetCategory and AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN != 0
                    )
                }
                .map { info ->
                    info.toWidgetProviderInfo(
                        user = profile.user,
                        profileSerial = profile.serial,
                        profileKind = profile.kind,
                        appIcon = appIconFor(packageName = info.provider.packageName, user = profile.user, iconPack = iconPack)
                    )
                }
        }
    }

    private fun ownerOf(info: AppWidgetProviderInfo): UserHandle? = profileSource.profiles.value.firstNotNullOfOrNull { profile ->
        profile.user.takeIf { user -> hasProvider(user = user, info = info) }
    }

    private fun hasProvider(user: UserHandle, info: AppWidgetProviderInfo): Boolean = runCatching {
        appWidgetManager.getInstalledProvidersForProfile(user)
    }.getOrDefault(emptyList()).any { candidate -> candidate.provider == info.provider }

    private fun trackReplacingPackage(change: ProfileChange) {
        when (change) {
            is ProfileChange.PackagesUnavailable -> {
                if (change.replacing) {
                    replacingPackages.addAll(change.packageNames)
                }
            }

            is ProfileChange.PackageRemoved -> Unit

            is ProfileChange.PackageAdded -> replacingPackages.remove(change.packageName)

            is ProfileChange.PackageChanged -> replacingPackages.remove(change.packageName)

            is ProfileChange.ProfileAdded,
            is ProfileChange.ProfileRemoved,
            is ProfileChange.ProfileAvailable,
            is ProfileChange.ProfileUnavailable -> Unit
        }
    }

    private fun AppWidgetProviderInfo.toWidgetProviderInfo(
        user: UserHandle,
        profileSerial: Long,
        profileKind: ProfileKind,
        appIcon: Icon
    ): WidgetProviderInfo {
        val appLabel: String = appLabelFor(provider.packageName, user)
        val features: Int = widgetFeatures
        val density: Float = context.resources.displayMetrics.density
        val isConfigurationOptional: Boolean =
            features and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL != 0
        val isReconfigurable: Boolean = features and AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE != 0
        return WidgetProviderInfo(
            provider = WidgetProvider(packageName = provider.packageName, className = provider.className),
            profileSerial = profileSerial,
            appPackageName = provider.packageName,
            appLabel = appLabel,
            appIcon = appIcon,
            label = loadLabel(context.packageManager).toString(),
            description = loadDescription(context)?.toString(),
            defaultSize = WidgetSize(widthDp = minWidth.pxToDp(density = density), heightDp = minHeight.pxToDp(density = density)),
            cellSize = if (targetCellWidth > 0 && targetCellHeight > 0) {
                WidgetSize(widthDp = targetCellWidth, heightDp = targetCellHeight)
            } else {
                null
            },
            minHeightDp = minHeight.pxToDp(density = density),
            minResizeHeightDp = minResizeHeight.pxToDp(density = density),
            maxResizeHeightDp = maxResizeHeight.takeIf { height -> height > 0 }?.pxToDp(density = density),
            needsConfiguration = needsConfiguration(
                hasConfigureActivity = configure != null,
                isConfigurationOptional = isConfigurationOptional,
                isReconfigurable = isReconfigurable
            ),
            isReconfigurable = isReconfigurable,
            hasConfigureActivity = configure != null,
            isWorkProfile = profileKind == ProfileKind.Work,
            isVerticallyResizable = resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL != 0
        )
    }

    private suspend fun appIconFor(packageName: String, user: UserHandle, iconPack: String): Icon {
        val componentName: ComponentName? = runCatching {
            launcherApps.getActivityList(packageName, user).firstOrNull()?.componentName
        }.getOrNull()
        if (componentName == null) {
            return Icon.ApplicationIcon(packageName)
        }
        return iconRepository.getIcon(packPackageName = iconPack, componentName = componentName)
    }

    private fun appLabelFor(packageName: String, user: UserHandle): String = runCatching {
        launcherApps.getApplicationInfo(packageName, 0, user).loadLabel(context.packageManager).toString()
    }.recoverCatching {
        context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(packageName, 0)).toString()
    }.getOrDefault(packageName)

    private fun Int.pxToDp(density: Float): Int = (this / density).roundToInt()
}
