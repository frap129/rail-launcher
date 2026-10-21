package core.lifecycle

import app.cash.turbine.test
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeSignalsTest {
    @Test
    fun `signal sent before a collector attaches is delivered once it attaches`() = runTest {
        val homeSignals: HomeSignals = HomeSignals()
        homeSignals.signal()
        homeSignals.events.test {
            assertEquals(Unit, awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun `consumed signal is not re-delivered to a later collector`() = runTest {
        val homeSignals: HomeSignals = HomeSignals()
        homeSignals.signal()
        homeSignals.events.first()
        homeSignals.events.test {
            expectNoEvents()
        }
    }

    @Test
    fun `signals sent while no collector is attached conflate into one delivery`() = runTest {
        val homeSignals: HomeSignals = HomeSignals()
        repeat(3) { homeSignals.signal() }
        homeSignals.events.test {
            assertEquals(Unit, awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun `signal sent to an attached collector is delivered`() = runTest {
        val homeSignals: HomeSignals = HomeSignals()
        homeSignals.events.test {
            homeSignals.signal()
            assertEquals(Unit, awaitItem())
        }
    }

    @Test
    fun `each signal is delivered after the previous one is consumed`() = runTest {
        val homeSignals: HomeSignals = HomeSignals()
        homeSignals.events.test {
            homeSignals.signal()
            assertEquals(Unit, awaitItem())
            homeSignals.signal()
            assertEquals(Unit, awaitItem())
        }
    }
}
