package core.lifecycle

import android.app.Application
import android.content.Context
import androidx.room.Room
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import core.data.apps.AppRepository
import core.data.icons.IconMapper
import core.data.icons.IconRepository
import core.data.launcher.LauncherItemRepository
import core.data.prefs.PreferencesRepository
import core.data.room.AppDatabase
import core.data.room.dao.CustomIconPackDao
import core.data.room.dao.PackIconDao
import feature.launcher.LauncherViewModel
import feature.settings.SettingsViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.context.startKoin
import org.koin.dsl.module
import timber.log.Timber

class MainApplication :
    Application(),
    SingletonImageLoader.Factory {
    private val database by lazy {
        Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "app-database"
        ).build()
    }

    /**
     * A Koin module containing a reference to the MainApplication class.
     *
     * @property appModule Koin Module for the Application object
     */
    private val appModule = module {
        single<MainApplication> { this@MainApplication }
    }

    /**
     * A Koin module containing references to Database Access Objects.
     *
     * @property daoModule Koin Module for the Application object
     */
    private val daoModule = module {
        single<CustomIconPackDao> { database.getCachedIconPackDao() }
        single<PackIconDao> { database.getPackIconDao() }
    }

    /**
     * A Koin module containing references to each repository class used
     * throughout the project.
     *
     * @property repoModule Koin Module for Repositories
     */
    private val repoModule = module {
        single<AppRepository> { AppRepository(androidContext()) }
        single<IconRepository> { IconRepository(androidContext(), get(), get()) }
        single<PreferencesRepository> { PreferencesRepository(androidContext()) }
        single<LauncherItemRepository> { LauncherItemRepository(get(), get(), get()) }
    }

    /**
     * A Koin module containing references to each ViewModel class used
     * throughout the project.
     *
     * @property viewModelModule Koin Module for ViewModels
     */
    private val viewModelModule = module {
        viewModel { LauncherViewModel(androidContext(), get(), get()) }
        viewModel { SettingsViewModel(get(), get()) }
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

    private object ReleaseTree : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            // TODO: Configure reporting for release builds
        }
    }

    override fun newImageLoader(context: PlatformContext) = ImageLoader.Builder(context)
        .components {
            add(IconMapper())
        }
        .build()
}
