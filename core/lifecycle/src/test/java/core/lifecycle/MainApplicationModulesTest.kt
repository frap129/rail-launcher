package core.lifecycle

import core.data.cleanup.IStaleKeyCleanup
import core.data.cleanup.IStoredKeyClassifier
import core.data.icons.IIconPackSource
import core.data.launchables.ILaunchableRepository
import core.data.popups.IPopupRepository
import core.data.prefs.IPreferencesRepository
import core.data.profiles.IPrivateSpaceStatus
import core.data.shortcuts.IPopupShortcutPinSource
import core.data.shortcuts.IShortcutPickerSource
import core.data.shortcuts.IShortcutPinCoordinator
import core.data.shortcuts.IStandaloneShortcutPinSource
import core.data.shortcuts.IStandaloneShortcutRepository
import core.data.shortcuts.IStandaloneShortcutSource
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
        assertNotNull(koin.get<IIconPackSource>())
        assertNotNull(koin.get<IPrivateSpaceStatus>())
        assertNotNull(koin.get<ILaunchableRepository>())
        assertNotNull(koin.get<IShortcutPickerSource>())
        assertNotNull(koin.get<IPopupShortcutPinSource>())
        assertNotNull(koin.get<IStandaloneShortcutSource>())
        assertNotNull(koin.get<IStandaloneShortcutPinSource>())
        assertNotNull(koin.get<IShortcutPinCoordinator>())
        assertNotNull(koin.get<IStandaloneShortcutRepository>())
        assertNotNull(koin.get<IPopupRepository>())
        assertNotNull(koin.get<IStoredKeyClassifier>())
        assertNotNull(koin.get<IStaleKeyCleanup>())
    }
}
