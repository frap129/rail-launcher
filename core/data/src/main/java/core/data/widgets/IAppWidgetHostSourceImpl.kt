package core.data.widgets

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.os.Bundle
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import androidx.core.content.ContextCompat
import core.data.icons.IIconRepository
import core.data.icons.model.Icon
import core.data.icons.model.SYSTEM_ICON_PACK_NAME
import core.data.prefs.IPreferencesRepository
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
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
    private val callbacks: AppWidgetHostCallbacks
) : IAppWidgetHostSource {
    private val appWidgetManager: AppWidgetManager = AppWidgetManager.getInstance(context)
    private val userManager: UserManager = context.getSystemService(UserManager::class.java)
    private val launcherApps: LauncherApps = context.getSystemService(LauncherApps::class.java)

    @Volatile
    private var cachedProviders: List<WidgetProviderInfo> = emptyList()

    private val broadcastChanges: MutableSharedFlow<Unit> = MutableSharedFlow(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val replacingPackages: MutableSet<String> = ConcurrentHashMap.newKeySet()

    private val receiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val packageName: String? = intent.data?.schemeSpecificPart
            when (intent.action) {
                Intent.ACTION_PACKAGE_REMOVED -> {
                    if (packageName != null && intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
                        replacingPackages.add(packageName)
                    }
                }

                Intent.ACTION_PACKAGE_ADDED,
                Intent.ACTION_PACKAGE_REPLACED,
                Intent.ACTION_PACKAGE_CHANGED -> {
                    packageName?.let { replacingPackages.remove(it) }
                }
            }
            broadcastChanges.tryEmit(Unit)
        }
    }

    init {
        val filter: IntentFilter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_MANAGED_PROFILE_ADDED)
            addAction(Intent.ACTION_MANAGED_PROFILE_REMOVED)
            addAction(Intent.ACTION_MANAGED_PROFILE_AVAILABLE)
            addAction(Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override val providerChanges: Flow<Unit> = merge(callbacks.providerChanges, broadcastChanges).conflate()

    override val providers: Flow<List<WidgetProviderInfo>> = providerChanges
        .onStart { emit(Unit) }
        .map {
            val loaded: List<WidgetProviderInfo> = loadProviders()
            cachedProviders = loaded
            loaded
        }
        .flowOn(Dispatchers.IO)

    override fun allocateId(): AppWidgetId = AppWidgetId(appWidgetHost.allocateAppWidgetId())

    override fun deleteId(id: AppWidgetId) {
        try {
            appWidgetHost.deleteAppWidgetId(id.value)
        } catch (exception: IllegalArgumentException) {
            Timber.w(exception, "Could not delete App Widget %d", id.value)
        }
    }

    override fun bindIfAllowed(request: BindRequest): Boolean {
        val user: UserHandle = userForSerial(request.profileSerial) ?: return false
        val provider: ComponentName = ComponentName(request.provider.packageName, request.provider.className)
        return appWidgetManager.bindAppWidgetIdIfAllowed(request.id.value, user, provider, null)
    }

    override fun boundIds(): Set<AppWidgetId> = appWidgetHost.appWidgetIds.mapTo(mutableSetOf()) { id -> AppWidgetId(id) }

    override fun availability(id: AppWidgetId, provider: WidgetProvider, profileSerial: Long): WidgetAvailability {
        val user: UserHandle = userForSerial(profileSerial) ?: return WidgetAvailability.Gone
        if (provider.packageName in replacingPackages) {
            return WidgetAvailability.Updating
        }
        val appInfo: ApplicationInfo = runCatching {
            launcherApps.getApplicationInfo(provider.packageName, 0, user)
        }.getOrNull() ?: return WidgetAvailability.Gone
        if (runCatching { userManager.isQuietModeEnabled(user) }.getOrDefault(false)) {
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
        val provider: WidgetProvider = WidgetProvider(packageName = info.provider.packageName, className = info.provider.className)
        cachedProviders.firstOrNull { cached -> cached.provider == provider }?.let { cached -> return cached }
        val user: UserHandle = ownerOf(info) ?: Process.myUserHandle()
        return info.toWidgetProviderInfo(user = user, appIcon = Icon.ApplicationIcon(info.provider.packageName))
    }

    override fun providerInfo(provider: WidgetProvider, profileSerial: Long): WidgetProviderInfo? =
        cachedProviders.firstOrNull { cached -> cached.provider == provider && cached.profileSerial == profileSerial }

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
        return userManager.userProfiles.flatMap { user ->
            runCatching { appWidgetManager.getInstalledProvidersForProfile(user) }
                .onFailure { exception -> Timber.w(exception, "Could not load App Widget providers for %s", user) }
                .getOrDefault(emptyList())
                .filter { info ->
                    isPickerProvider(
                        isOwnPackage = info.provider.packageName == context.packageName,
                        isHomeScreenCategory = info.widgetCategory and AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN != 0
                    )
                }
                .map { info ->
                    info.toWidgetProviderInfo(
                        user = user,
                        appIcon = appIconFor(packageName = info.provider.packageName, user = user, iconPack = iconPack)
                    )
                }
        }
    }

    private fun ownerOf(info: AppWidgetProviderInfo): UserHandle? = userManager.userProfiles.firstOrNull { user ->
        runCatching { appWidgetManager.getInstalledProvidersForProfile(user) }
            .getOrDefault(emptyList())
            .any { candidate -> candidate.provider == info.provider }
    }

    private fun userForSerial(serial: Long): UserHandle? =
        userManager.userProfiles.firstOrNull { user -> userManager.getSerialNumberForUser(user) == serial }

    private fun AppWidgetProviderInfo.toWidgetProviderInfo(user: UserHandle, appIcon: Icon): WidgetProviderInfo {
        val appLabel: String = appLabelFor(provider.packageName, user)
        val features: Int = widgetFeatures
        val isConfigurationOptional: Boolean =
            features and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL != 0
        val isReconfigurable: Boolean = features and AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE != 0
        return WidgetProviderInfo(
            provider = WidgetProvider(packageName = provider.packageName, className = provider.className),
            profileSerial = userManager.getSerialNumberForUser(user),
            appPackageName = provider.packageName,
            appLabel = appLabel,
            appIcon = appIcon,
            label = loadLabel(context.packageManager).toString(),
            description = loadDescription(context)?.toString(),
            defaultSize = WidgetSize(widthDp = minWidth, heightDp = minHeight),
            cellSize = if (targetCellWidth > 0 && targetCellHeight > 0) {
                WidgetSize(widthDp = targetCellWidth, heightDp = targetCellHeight)
            } else {
                null
            },
            minHeightDp = minHeight,
            minResizeHeightDp = minResizeHeight,
            maxResizeHeightDp = maxResizeHeight.takeIf { height -> height > 0 },
            needsConfiguration = needsConfiguration(
                hasConfigureActivity = configure != null,
                isConfigurationOptional = isConfigurationOptional,
                isReconfigurable = isReconfigurable
            ),
            isReconfigurable = isReconfigurable,
            hasConfigureActivity = configure != null,
            isWorkProfile = user != Process.myUserHandle()
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
}
