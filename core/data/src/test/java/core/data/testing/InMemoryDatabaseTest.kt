package core.data.testing

import android.graphics.Bitmap
import androidx.room.Room
import androidx.room.RoomDatabase
import core.data.favorites.FavoriteRecord
import core.data.icons.IIconImageStore
import core.data.icons.IIconImageStoreImpl
import core.data.icons.IIconPackSource
import core.data.icons.IIconRepositoryImpl
import core.data.prefs.IPreferencesRepository
import core.data.prefs.IPreferencesRepositoryImpl
import core.data.prefs.LaunchableCustomization
import core.data.profiles.IProfileSource
import core.data.room.AppDatabase
import core.data.room.dao.LaunchableCustomizationDao
import core.data.room.dao.SettingsDao
import core.data.room.dao.WidgetAreaDao
import core.data.testing.finishPendingWork as finishAllPendingWork
import core.data.widgets.WidgetPlaceRecord
import core.data.widgets.WidgetRecord
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Runs against a fresh in-memory Room database, so DAOs behave as they do in the app. Its query
 * and transaction work runs on [databaseDispatcher], so Room advances with the test scheduler;
 * run test bodies through [databaseTest].
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [TEST_SDK])
abstract class InMemoryDatabaseTest {
    protected val databaseDispatcher: TestDispatcher = StandardTestDispatcher()

    lateinit var database: AppDatabase
        private set

    /** Runs [testBody] with Room work on [databaseDispatcher] so it advances with the scheduler. */
    protected fun databaseTest(testBody: suspend TestScope.() -> Unit): TestResult =
        runTest(context = databaseDispatcher, testBody = testBody)

    @Before
    fun createDatabase() {
        val builder: RoomDatabase.Builder<AppDatabase> =
            Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
        database = configureDatabaseBuilder(builder).configureForTest().build()
    }

    /**
     * Adds callbacks or other builder options. The test-controlled execution is applied afterwards,
     * so a database this base builds always runs its queries on the test scheduler.
     */
    protected open fun configureDatabaseBuilder(builder: RoomDatabase.Builder<AppDatabase>): RoomDatabase.Builder<AppDatabase> = builder

    /** Gives a database the main-thread access and test-controlled execution [databaseTest] expects. */
    protected fun RoomDatabase.Builder<AppDatabase>.configureForTest(): RoomDatabase.Builder<AppDatabase> =
        allowMainThreadQueries().setQueryCoroutineContext(databaseDispatcher)

    /** Lets all pending work finish through the shared test scope helper. */
    protected fun TestScope.finishPendingWork() {
        finishAllPendingWork()
    }

    /** A real [IIconImageStore] over the test app's private files directory. */
    fun createIconImageStore(): IIconImageStore = IIconImageStoreImpl(context = RuntimeEnvironment.getApplication())

    /** A real [IIconImageStore] holding an image for each of [fileNames]. */
    fun createImageStoreWithFiles(vararg fileNames: String): IIconImageStore = createIconImageStore().apply {
        fileNames.forEach { fileName ->
            assertTrue(write(fileName = fileName, bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)))
        }
    }

    /** A real [IPreferencesRepository] over the test database, writing on this test scope. */
    fun TestScope.createPreferencesRepository(
        launchableCustomizationDao: LaunchableCustomizationDao = database.getLaunchableCustomizationDao(),
        settingsDao: SettingsDao = database.getSettingsDao(),
        iconImageStore: IIconImageStore = createIconImageStore(),
        dispatcher: CoroutineDispatcher = databaseDispatcher
    ): IPreferencesRepository = IPreferencesRepositoryImpl(
        launchableCustomizationDao = launchableCustomizationDao,
        settingsDao = settingsDao,
        imageStore = iconImageStore,
        scope = this,
        dispatcher = dispatcher
    )

    /** A real [IIconRepositoryImpl] over the test database, reading packs through [iconPackSource]. */
    fun TestScope.createIconRepository(
        iconPackSource: IIconPackSource,
        profileSource: IProfileSource = createProfileSourceMock(),
        scope: CoroutineScope = backgroundScope,
        dispatcher: CoroutineDispatcher = databaseDispatcher
    ): IIconRepositoryImpl = IIconRepositoryImpl(
        context = RuntimeEnvironment.getApplication(),
        iconPackDao = database.getCachedIconPackDao(),
        iconDao = database.getPackIconDao(),
        iconPackSource = iconPackSource,
        imageStore = createIconImageStore(),
        profileSource = profileSource,
        scope = scope,
        dispatcher = dispatcher
    )

    @After
    fun closeDatabase() {
        database.close()
    }

    fun widgetAreaDao(): WidgetAreaDao = database.getWidgetAreaDao()

    /** Seeds the Widget Area rows a production write would store. */
    suspend fun seedWidgetArea(places: List<WidgetPlaceRecord> = emptyList(), widgets: List<WidgetRecord> = emptyList()) {
        places.forEach { place -> widgetAreaDao().insertPlace(place) }
        widgets.forEach { widget -> widgetAreaDao().insertWidget(widget) }
    }

    /** Stores a Favorite for [key] at [position], as the Favorites repository would leave it. */
    suspend fun seedFavorite(key: String, position: Int) {
        database.getFavoriteDao().add(key)
        database.getFavoriteDao().update(listOf(FavoriteRecord(key = key, position = position)))
    }

    suspend fun favoriteRecords(): List<FavoriteRecord> = database.getFavoriteDao().observeAll().first()

    /** The stored Launchable name customizations, optionally waiting for [predicate]. */
    suspend fun customizations(predicate: (List<LaunchableCustomization>) -> Boolean = { true }): List<LaunchableCustomization> =
        database.getLaunchableCustomizationDao().observeAll().first(predicate)
}
