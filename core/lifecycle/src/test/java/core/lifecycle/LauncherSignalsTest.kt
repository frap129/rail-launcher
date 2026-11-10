package core.lifecycle

import app.cash.turbine.test
import core.ui.model.data.LauncherSignal
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherSignalsTest {
    @Test
    fun `signal sent before a collector attaches is delivered once it attaches`() = runTest {
        val launcherSignals: LauncherSignals = LauncherSignals()
        launcherSignals.signal(LauncherSignal.Home(wasResumed = true))
        launcherSignals.events.test {
            assertEquals(LauncherSignal.Home(wasResumed = true), awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun `consumed signal is not re-delivered to a later collector`() = runTest {
        val launcherSignals: LauncherSignals = LauncherSignals()
        launcherSignals.signal(LauncherSignal.Home(wasResumed = true))
        launcherSignals.events.first()
        launcherSignals.events.test {
            expectNoEvents()
        }
    }

    @Test
    fun `signals sent while no collector is attached conflate into the latest signal`() = runTest {
        val launcherSignals: LauncherSignals = LauncherSignals()
        launcherSignals.signal(LauncherSignal.Home(wasResumed = true))
        launcherSignals.signal(LauncherSignal.Home(wasResumed = false))
        launcherSignals.events.test {
            assertEquals(LauncherSignal.Home(wasResumed = false), awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun `signal sent to an attached collector is delivered`() = runTest {
        val launcherSignals: LauncherSignals = LauncherSignals()
        launcherSignals.events.test {
            launcherSignals.signal(LauncherSignal.Home(wasResumed = false))
            assertEquals(LauncherSignal.Home(wasResumed = false), awaitItem())
        }
    }

    @Test
    fun `each signal is delivered after the previous one is consumed`() = runTest {
        val launcherSignals: LauncherSignals = LauncherSignals()
        launcherSignals.events.test {
            launcherSignals.signal(LauncherSignal.Home(wasResumed = true))
            assertEquals(LauncherSignal.Home(wasResumed = true), awaitItem())
            launcherSignals.signal(LauncherSignal.Home(wasResumed = false))
            assertEquals(LauncherSignal.Home(wasResumed = false), awaitItem())
        }
    }

    @Test
    fun `typed characters sent before a collector attaches are all delivered in order`() = runTest {
        val launcherSignals: LauncherSignals = LauncherSignals()
        launcherSignals.signal(LauncherSignal.TypedCharacter(character = "c"))
        launcherSignals.signal(LauncherSignal.TypedCharacter(character = "a"))
        launcherSignals.signal(LauncherSignal.TypedCharacter(character = "l"))
        launcherSignals.events.test {
            assertEquals(LauncherSignal.TypedCharacter(character = "c"), awaitItem())
            assertEquals(LauncherSignal.TypedCharacter(character = "a"), awaitItem())
            assertEquals(LauncherSignal.TypedCharacter(character = "l"), awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun `typed characters sent to an attached collector are not conflated`() = runTest {
        val launcherSignals: LauncherSignals = LauncherSignals()
        launcherSignals.events.test {
            launcherSignals.signal(LauncherSignal.TypedCharacter(character = "c"))
            assertEquals(LauncherSignal.TypedCharacter(character = "c"), awaitItem())
            launcherSignals.signal(LauncherSignal.TypedCharacter(character = "a"))
            assertEquals(LauncherSignal.TypedCharacter(character = "a"), awaitItem())
            launcherSignals.signal(LauncherSignal.TypedCharacter(character = "l"))
            assertEquals(LauncherSignal.TypedCharacter(character = "l"), awaitItem())
        }
    }
}
