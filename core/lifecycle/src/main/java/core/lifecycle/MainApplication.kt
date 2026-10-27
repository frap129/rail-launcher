package core.lifecycle

import android.app.Activity
import android.app.Application
import android.appwidget.AppWidgetHost
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import core.data.apps.IAppRepository
import core.data.apps.IAppRepositoryImpl
import core.data.battery.IBatterySource
import core.data.battery.IBatterySourceImpl
import core.data.calendar.ICalendarEventSource
import core.data.calendar.ICalendarEventSourceImpl
import core.data.calendar.ICalendarPermissionGate
import core.data.calendar.ICalendarPermissionGateImpl
import core.data.favorites.IFavoritesRepository
import core.data.favorites.IFavoritesRepositoryImpl
import core.data.folders.IFolderRepository
import core.data.folders.IFolderRepositoryImpl
import core.data.icons.IIconRepository
import core.data.icons.IIconRepositoryImpl
import core.data.icons.IconFetcher
import core.data.icons.IconKeyer
import core.data.launchables.ILaunchableRepository
import core.data.launchables.ILaunchableRepositoryImpl
import core.data.media.IMediaSessionSource
import core.data.media.IMediaSessionSourceImpl
import core.data.media.MediaArtworkFetcher
import core.data.media.MediaArtworkKeyer
import core.data.media.MediaArtworkStore
import core.data.notifications.INotificationListenerRelay
import core.data.notifications.INotificationListenerRelayImpl
import core.data.notifications.INotificationSource
import core.data.notifications.INotificationSourceImpl
import core.data.popups.IPopupRepository
import core.data.popups.IPopupRepositoryImpl
import core.data.prefs.IPreferencesRepository
import core.data.prefs.IPreferencesRepositoryImpl
import core.data.room.AppDatabase
import core.data.room.MIGRATION_11_12
import core.data.room.dao.CustomIconPackDao
import core.data.room.dao.EventSettingsDao
import core.data.room.dao.FavoriteDao
import core.data.room.dao.FolderDao
import core.data.room.dao.HeaderSettingsDao
import core.data.room.dao.LaunchableCustomizationDao
import core.data.room.dao.MediaPlayerSettingsDao
import core.data.room.dao.PackIconDao
import core.data.room.dao.PendingAppWidgetDao
import core.data.room.dao.PopupDao
import core.data.room.dao.SettingsDao
import core.data.room.dao.WidgetAreaDao
import core.data.room.dao.WidgetAreaSettingsDao
import core.data.shortcuts.IShortcutSource
import core.data.shortcuts.IShortcutSourceImpl
import core.data.time.IClockSource
import core.data.time.IClockSourceImpl
import core.data.time.IDatePatternSource
import core.data.time.IDatePatternSourceImpl
import core.data.widgets.AppWidgetHostCallbacks
import core.data.widgets.IAddFlowPendingIds
import core.data.widgets.IAddFlowPendingIdsImpl
import core.data.widgets.IAppWidgetHostSource
import core.data.widgets.IAppWidgetHostSourceImpl
import core.data.widgets.IHeaderSettingsActions
import core.data.widgets.IHeaderSettingsActionsImpl
import core.data.widgets.IRemoveAppWidget
import core.data.widgets.IRemoveAppWidgetImpl
import core.data.widgets.IRemoveOwnerWidgets
import core.data.widgets.IRemoveOwnerWidgetsImpl
import core.data.widgets.IWidgetAreaRepository
import core.data.widgets.IWidgetAreaRepositoryImpl
import core.data.widgets.IWidgetLifecycleReconciler
import core.data.widgets.IWidgetLifecycleReconcilerImpl
import core.data.widgets.IWidgetSettingsRepository
import core.data.widgets.IWidgetSettingsRepositoryImpl
import core.data.widgets.WidgetAreaSeedCallback
import core.lifecycle.widgets.IAppWidgetViewCacheImpl
import core.lifecycle.widgets.RailAppWidgetHost
import core.ui.layout.HomeEditState
import core.ui.layout.HomeHeightState
import core.ui.popups.IPopupCollaborators
import core.ui.popups.IPopupCollaboratorsImpl
import core.ui.popups.IPopupEditor
import core.ui.popups.IPopupEditorImpl
import core.ui.popups.IPopupFolderActions
import core.ui.popups.IPopupFolderActionsImpl
import core.ui.popups.IPopupPicker
import core.ui.popups.IPopupPickerImpl
import core.ui.popups.IPopupSession
import core.ui.popups.IPopupSessionImpl
import core.ui.widgets.HeaderFormatter
import core.ui.widgets.IAppWidgetViewCache
import core.ui.widgets.IHeaderTapTargetResolver
import core.ui.widgets.IHeaderTapTargetResolverImpl
import core.util.screenHeightPx
import feature.launcher.LauncherViewModel
import feature.launcher.ListOffsets
import feature.launcher.PopupCardViewModel
import feature.launcher.widgets.AppWidgetResults
import feature.launcher.widgets.IWidgetAddController
import feature.launcher.widgets.IWidgetAddControllerImpl
import feature.launcher.widgets.IWidgetResizeController
import feature.launcher.widgets.IWidgetResizeControllerImpl
import feature.launcher.widgets.WidgetAreaViewModel
import feature.settings.PopupSettingsViewModel
import feature.settings.SettingsViewModel
import feature.settings.WidgetSettingsViewModel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.viewModel
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.named
import org.koin.dsl.module
import timber.log.Timber

private const val APP_SCOPE_QUALIFIER: String = "appScope"
private const val RELEASE_LOG_TAG: String = "Rail"

class MainApplication :
    Application(),
    SingletonImageLoader.Factory,
    KoinComponent {
    private val appScope: CoroutineScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, throwable ->
            Timber.e(throwable, "Uncaught exception in the application scope")
        }
    )

    private val database by lazy {
        Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "app-database"
        )
            .addCallback(WidgetAreaSeedCallback())
            .addMigrations(MIGRATION_11_12)
            // Pre-release only: replace with real migrations before the first public release.
            .fallbackToDestructiveMigration(dropAllTables = false)
            .build()
    }

    /**
     * A Koin module containing a reference to the MainApplication class.
     *
     * @property appModule Koin Module for the Application object
     */
    private val appModule = module {
        single<MainApplication> { this@MainApplication }
        single<CoroutineScope>(named(APP_SCOPE_QUALIFIER)) { appScope }
    }

    /**
     * A Koin module containing references to Database Access Objects.
     *
     * @property daoModule Koin Module for the Application object
     */
    private val daoModule = module {
        single<CustomIconPackDao> { database.getCachedIconPackDao() }
        single<PackIconDao> { database.getPackIconDao() }
        single<LaunchableCustomizationDao> { database.getLaunchableCustomizationDao() }
        single<SettingsDao> { database.getSettingsDao() }
        single<HeaderSettingsDao> { database.getHeaderSettingsDao() }
        single<EventSettingsDao> { database.getEventSettingsDao() }
        single<FavoriteDao> { database.getFavoriteDao() }
        single<WidgetAreaDao> { database.getWidgetAreaDao() }
        single<WidgetAreaSettingsDao> { database.getWidgetAreaSettingsDao() }
        single<MediaPlayerSettingsDao> { database.getMediaPlayerSettingsDao() }
        single<PendingAppWidgetDao> { database.getPendingAppWidgetDao() }
        single<PopupDao> { database.getPopupDao() }
        single<FolderDao> { database.getFolderDao() }
    }

    /**
     * A Koin module containing references to each repository class used
     * throughout the project.
     *
     * @property repoModule Koin Module for Repositories
     */
    private val repoModule = module {
        single<IAppRepository> { IAppRepositoryImpl(androidContext(), get(named(APP_SCOPE_QUALIFIER))) }
        single<IIconRepository> { IIconRepositoryImpl(androidContext(), get(), get(), get(named(APP_SCOPE_QUALIFIER))) }
        single<IPreferencesRepository> { IPreferencesRepositoryImpl(get(), get(), get(named(APP_SCOPE_QUALIFIER))) }
        single<ILaunchableRepository> { ILaunchableRepositoryImpl(get(), get(), get(), get(), get(), get(named(APP_SCOPE_QUALIFIER))) }
        single<IFavoritesRepository> { IFavoritesRepositoryImpl(get(), get(), get(named(APP_SCOPE_QUALIFIER))) }
        single<IShortcutSource> { IShortcutSourceImpl(androidContext(), get()) }
        single<IPopupRepository> { IPopupRepositoryImpl(get(), get(), get(), get(named(APP_SCOPE_QUALIFIER)), get(), get()) }
        single<IFolderRepository> { IFolderRepositoryImpl(get(), get(), get(), get(), get()) }
        single<IWidgetAreaRepository> { IWidgetAreaRepositoryImpl(get(), get(), get()) }
        single<IWidgetSettingsRepository> { IWidgetSettingsRepositoryImpl(get(), get(), get(), get(), get()) }
        single { MediaArtworkStore() }
        single<IMediaSessionSource> {
            IMediaSessionSourceImpl(
                context = androidContext(),
                scope = get(named(APP_SCOPE_QUALIFIER)),
                artworkStore = get()
            )
        }
        single<INotificationListenerRelay> { INotificationListenerRelayImpl() }
        single<INotificationSource> {
            INotificationSourceImpl(
                context = androidContext(),
                scope = get(named(APP_SCOPE_QUALIFIER)),
                popupRepository = get(),
                relay = get()
            )
        }
        single { AppWidgetHostCallbacks() }
        single<AppWidgetHost> { RailAppWidgetHost(androidContext(), IAppWidgetHostSource.HOST_ID, get()) }
        single<AppWidgetResults> { AppWidgetResults() }
        single<IAddFlowPendingIds> { IAddFlowPendingIdsImpl(get()) }
        single<IWidgetLifecycleReconciler> { IWidgetLifecycleReconcilerImpl(get(), get()) }
        single<IAppWidgetHostSource> { IAppWidgetHostSourceImpl(androidContext(), get(), get(), get(), get()) }
        single<IAppWidgetViewCache> { IAppWidgetViewCacheImpl(androidContext(), get()) }
        single<IRemoveAppWidget> { IRemoveAppWidgetImpl(get(), get()) }
        single<IRemoveOwnerWidgets> { IRemoveOwnerWidgetsImpl(get(), get()) }
        single<ICalendarEventSource> { ICalendarEventSourceImpl(androidContext()) }
        factory<ICalendarPermissionGate> { ICalendarPermissionGateImpl(calendarEventSource = get()) }
        factory<IHeaderSettingsActions> {
            IHeaderSettingsActionsImpl(settingsRepository = get(), calendarPermissionGate = get())
        }
        single<IHeaderTapTargetResolver> { IHeaderTapTargetResolverImpl() }
        factory<IWidgetAddController> { (savedStateHandle: SavedStateHandle) ->
            IWidgetAddControllerImpl(
                repository = get(),
                appWidgetHost = get(),
                settingsRepository = get(),
                homeEditState = get(),
                savedStateHandle = savedStateHandle,
                pendingIds = get()
            )
        }
        factory<IWidgetResizeController> {
            IWidgetResizeControllerImpl(
                repository = get(),
                appWidgetHost = get(),
                settingsRepository = get(),
                homeEditState = get()
            )
        }
        factory<IPopupSession> { IPopupSessionImpl() }
        factory<IPopupCollaborators> { (session: IPopupSession) ->
            IPopupCollaboratorsImpl(
                session = session,
                editor = get { parametersOf(session) },
                picker = get { parametersOf(session) },
                folder = get { parametersOf(session) }
            )
        }
        factory<IPopupEditor> { (session: IPopupSession) ->
            IPopupEditorImpl(
                parentScope = get(named(APP_SCOPE_QUALIFIER)),
                launchableRepository = get(),
                shortcutSource = get(),
                popupRepository = get(),
                homeEditState = get(),
                session = session
            )
        }
        factory<IPopupPicker> { (session: IPopupSession) ->
            IPopupPickerImpl(
                parentScope = get(named(APP_SCOPE_QUALIFIER)),
                launchableRepository = get(),
                shortcutSource = get(),
                popupRepository = get(),
                homeEditState = get(),
                session = session
            )
        }
        factory<IPopupFolderActions> { (session: IPopupSession) ->
            IPopupFolderActionsImpl(
                parentScope = get(named(APP_SCOPE_QUALIFIER)),
                launchableRepository = get(),
                popupRepository = get(),
                folderRepository = get(),
                preferencesRepository = get(),
                homeEditState = get(),
                session = session
            )
        }
        single { HomeEditState() }
        single { HomeHeightState() }
        single<IClockSource> { IClockSourceImpl(androidContext()) }
        single<IBatterySource> { IBatterySourceImpl(androidContext(), get(named(APP_SCOPE_QUALIFIER))) }
        single<IDatePatternSource> { IDatePatternSourceImpl() }
        single { HeaderFormatter(get(), androidContext().resources) }
    }

    /**
     * A Koin module containing references to each ViewModel class used
     * throughout the project.
     *
     * @property viewModelModule Koin Module for ViewModels
     */
    private val viewModelModule = module {
        factory { ListOffsets.forScreenHeight(screenHeightPx(androidContext())) }
        viewModel { LauncherViewModel(get(), get(), get(), get(), get(), get(), get(), get()) }
        viewModel { (session: IPopupSession) ->
            val savedStateHandle: SavedStateHandle = get()
            PopupCardViewModel(
                launchableRepository = get(),
                shortcutSource = get(),
                popupRepository = get(),
                notificationSource = get(),
                clockSource = get(),
                homeEditState = get(),
                widgetRepository = get(),
                addFlowController = get { parametersOf(savedStateHandle) },
                resizeController = get(),
                appWidgetHost = get(),
                removeAppWidget = get(),
                widgetLifecycleReconciler = get(),
                widgetSettingsRepository = get(),
                savedStateHandle = savedStateHandle,
                session = session,
                collaborators = get { parametersOf(session) }
            )
        }
        viewModel { SettingsViewModel(get(), get()) }
        viewModel { (session: IPopupSession) ->
            PopupSettingsViewModel(
                folderRepository = get(),
                popupRepository = get(),
                notificationSource = get(),
                favoritesRepository = get(),
                collaborators = get { parametersOf(session) }
            )
        }
        viewModel {
            WidgetSettingsViewModel(
                settingsRepository = get(),
                areaRepository = get(),
                clockSource = get(),
                formatter = get(),
                launchableRepository = get(),
                calendarEventSource = get(),
                appWidgetHost = get(),
                removeAppWidget = get(),
                homeHeightState = get(),
                mediaSessionSource = get(),
                headerSettingsActions = get(),
                tapTargetResolver = get()
            )
        }
        viewModel {
            val savedStateHandle: SavedStateHandle = get()
            WidgetAreaViewModel(
                repository = get(),
                clockSource = get(),
                formatter = get(),
                settingsRepository = get(),
                homeEditState = get(),
                launchableRepository = get(),
                calendarEventSource = get(),
                appWidgetHost = get(),
                batterySource = get(),
                removeAppWidget = get(),
                addFlowController = get { parametersOf(savedStateHandle) },
                widgetLifecycleReconciler = get(),
                homeHeightState = get(),
                mediaSessionSource = get(),
                headerSettingsActions = get(),
                tapTargetResolver = get(),
                resizeController = get()
            )
        }
    }

    override fun attachBaseContext(base: Context) {
        startKoin {
            androidContext(base)
            androidLogger()
            modules(appModule, daoModule, repoModule, viewModelModule)
        }
        super.attachBaseContext(base)
    }

    override fun onCreate() {
        super.onCreate()

        // Set up Timber
        when (BuildConfig.DEBUG) {
            true -> Timber.plant(Timber.DebugTree())
            false -> Timber.plant(ReleaseTree)
        }

        startWidgetLifecycle()
    }

    private fun startWidgetLifecycle() {
        val reconciler: IWidgetLifecycleReconciler = get()
        val pendingIds: IAddFlowPendingIds = get()
        val hostSource: IAppWidgetHostSource = get()
        val launchableRepository: ILaunchableRepository = get()
        val notificationSource: INotificationSource = get()
        registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            private var isReconciled: Boolean = false

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                if (isReconciled) {
                    return
                }
                isReconciled = true
                appScope.launch(Dispatchers.IO) {
                    reconciler.reconcileAtStartup(pendingIds = pendingIds.get())
                }
            }

            override fun onActivityStarted(activity: Activity) = Unit

            override fun onActivityResumed(activity: Activity) {
                notificationSource.refreshAccess()
            }

            override fun onActivityPaused(activity: Activity) = Unit

            override fun onActivityStopped(activity: Activity) = Unit

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

            override fun onActivityDestroyed(activity: Activity) = Unit
        })
        appScope.launch(Dispatchers.IO) {
            combine(
                launchableRepository.launchableState,
                hostSource.providerChanges.onStart { emit(Unit) }
            ) { state, _ -> state.isLoaded }
                .filter { isLoaded -> isLoaded }
                .collect { reconciler.removeGoneProviders() }
        }
    }

    /** Release logcat sink. A Timber Tree is the layer that writes to [Log]. */
    private object ReleaseTree : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            Log.println(priority, tag ?: RELEASE_LOG_TAG, message)
        }
    }

    override fun newImageLoader(context: PlatformContext) = ImageLoader.Builder(context)
        .components {
            add(IconKeyer())
            add(IconFetcher.Factory())
            add(MediaArtworkKeyer())
            add(MediaArtworkFetcher.Factory(store = get()))
        }
        .build()
}
