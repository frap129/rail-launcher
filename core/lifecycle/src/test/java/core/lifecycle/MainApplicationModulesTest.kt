package core.lifecycle

import core.data.launchables.ILaunchableRepository
import core.data.prefs.IPreferencesRepository
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = MainApplication::class, sdk = [35])
class MainApplicationModulesTest {
    @After
    fun stopKoinAfterTest() {
        stopKoin()
    }

    @Test
    fun `resolves the repository singletons without a dependency cycle`() {
        val koin = GlobalContext.get()

        assertNotNull(koin.get<IPreferencesRepository>())
        assertNotNull(koin.get<ILaunchableRepository>())
    }
}
