package core.lifecycle

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.room.Room
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import core.data.apps.IAppRepository
import core.data.apps.IAppRepositoryImpl
import core.data.battery.AndroidBatterySource
import core.data.battery.IBatterySource
import core.data.calendar.AndroidCalendarEventSource
import core.data.calendar.ICalendarEventSource
import core.data.favorites.IFavoritesRepository
import core.data.favorites.IFavoritesRepositoryImpl
import core.data.icons.IIconRepository
import core.data.icons.IIconRepositoryImpl
import core.data.icons.IconFetcher
import core.data.icons.IconKeyer
import core.data.launchables.ILaunchableRepository
import core.data.launchables.ILaunchableRepositoryImpl
import core.data.prefs.IPreferencesRepository
import core.data.prefs.IPreferencesRepositoryImpl
import core.data.room.AppDatabase
import core.data.room.dao.CustomIconPackDao
import core.data.room.dao.EventSettingsDao
import core.data.room.dao.FavoriteDao
import core.data.room.dao.HeaderSettingsDao
import core.data.room.dao.LaunchableCustomizationDao
import core.data.room.dao.PackIconDao
import core.data.room.dao.SettingsDao
import core.data.room.dao.WidgetAreaDao
import core.data.time.AndroidClockSource
import core.data.time.AndroidDatePatternSource
import core.data.time.IClockSource
import core.data.time.IDatePatternSource
import core.data.widgets.IWidgetAreaRepository
import core.data.widgets.IWidgetAreaRepositoryImpl
import core.data.widgets.IWidgetSettingsRepository
import core.data.widgets.IWidgetSettingsRepositoryImpl
import core.data.widgets.WidgetAreaSeedCallback
import core.util.screenHeightPx
import feature.launcher.LauncherViewModel
import feature.launcher.ListOffsets
import feature.settings.SettingsViewModel
import feature.settings.WidgetSettingsViewModel
import feature.widgets.HeaderFormatter
import feature.widgets.HomeEditState
import feature.widgets.WidgetAreaViewModel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module
import timber.log.Timber

private const val APP_SCOPE_QUALIFIER: String = "appScope"
private const val RELEASE_LOG_TAG: String = "Rail"

class MainApplication :
    Application(),
    SingletonImageLoader.Factory {
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
    }

    /**
     * A Koin module containing references to each repository class used
     * throughout the project.
     *
     * @property repoModule Koin Module for Repositories
     */
    private val repoModule = module {
        single<IAppRepository> { IAppRepositoryImpl(androidContext()) }
        single<IIconRepository> { IIconRepositoryImpl(androidContext(), get(), get()) }
        single<IPreferencesRepository> { IPreferencesRepositoryImpl(get(), get()) }
        single<ILaunchableRepository> { ILaunchableRepositoryImpl(get(), get(), get(), get(named(APP_SCOPE_QUALIFIER))) }
        single<IFavoritesRepository> { IFavoritesRepositoryImpl(get(), get(), get(named(APP_SCOPE_QUALIFIER))) }
        single<IWidgetAreaRepository> { IWidgetAreaRepositoryImpl(get()) }
        single<IWidgetSettingsRepository> { IWidgetSettingsRepositoryImpl(get(), get(), get()) }
        single<ICalendarEventSource> { AndroidCalendarEventSource(androidContext()) }
        single { HomeEditState() }
        single<IClockSource> { AndroidClockSource(androidContext()) }
        single<IBatterySource> { AndroidBatterySource(androidContext()) }
        single<IDatePatternSource> { AndroidDatePatternSource() }
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
        viewModel { LauncherViewModel(get(), get(), get(), get(), get()) }
        viewModel { SettingsViewModel(get(), get()) }
        viewModel { WidgetSettingsViewModel(get(), get(), get(), get(), get(), get()) }
        viewModel { WidgetAreaViewModel(get(), get(), get(), get(), get(), get(), get(), get()) }
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
        }
        .build()
}
