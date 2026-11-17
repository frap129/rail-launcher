package core.data.time

import android.app.Application
import android.content.Intent
import android.os.Looper
import android.provider.Settings
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.TurbineContext
import app.cash.turbine.test
import app.cash.turbine.testIn
import app.cash.turbine.turbineScope
import core.data.testing.TEST_NOW_EPOCH_MS
import core.data.testing.TEST_SDK
import core.util.MILLIS_PER_MINUTE
import core.util.WHILE_SUBSCRIBED_TIMEOUT_MS
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [TEST_SDK])
class IClockSourceImplTest {
    private val application: Application = RuntimeEnvironment.getApplication()

    @Test
    fun `shares one receiver, one observer and a current reading with every collector`() = runTest {
        val expectedFirstInstant: Instant = Instant.ofEpochMilli(TEST_NOW_EPOCH_MS)
        val expectedLaterInstant: Instant = Instant.ofEpochMilli(TEST_NOW_EPOCH_MS + MILLIS_PER_MINUTE)
        var inputInstant: Instant = expectedFirstInstant
        val source: IClockSourceImpl = createSource(currentInstant = { inputInstant })

        turbineScope {
            val firstTurbine: ReceiveTurbine<ClockReading> = source.readings.testIn(backgroundScope)
            runCurrent()
            val actualFirstReading: ClockReading = firstTurbine.awaitItem()
            assertEquals(expectedFirstInstant, actualFirstReading.instant)
            runCurrent()
            assertClockRegistrations(expectedReceiverCount = 1, expectedObserverCount = 1)

            inputInstant = expectedLaterInstant
            val secondTurbine: ReceiveTurbine<ClockReading> = source.readings.testIn(backgroundScope)
            runCurrent()
            val actualSecondReading: ClockReading = secondTurbine.awaitItem()
            assertEquals(expectedLaterInstant, actualSecondReading.instant)
            runCurrent()
            assertClockRegistrations(expectedReceiverCount = 1, expectedObserverCount = 1)

            firstTurbine.cancelAndIgnoreRemainingEvents()
            secondTurbine.cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updates every collector when the time changes`() = runTest {
        val expectedInstant: Instant = Instant.ofEpochMilli(TEST_NOW_EPOCH_MS + MILLIS_PER_MINUTE)
        var inputInstant: Instant = Instant.ofEpochMilli(TEST_NOW_EPOCH_MS)
        val source: IClockSourceImpl = createSource(currentInstant = { inputInstant })

        turbineScope {
            val (firstTurbine, secondTurbine) = createCollectors(testScope = this@runTest, source = source)
            val actualReading: ClockReading = awaitSharedReading(firstTurbine, secondTurbine) {
                inputInstant = expectedInstant
                application.sendBroadcast(Intent(Intent.ACTION_TIME_CHANGED))
            }

            assertEquals(expectedInstant, actualReading.instant)
            assertClockRegistrations(expectedReceiverCount = 1, expectedObserverCount = 1)
            firstTurbine.cancelAndIgnoreRemainingEvents()
            secondTurbine.cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updates every collector when the time zone changes`() = runTest {
        val expectedZone: ZoneId = ZoneId.of("Asia/Tokyo")
        var inputZone: ZoneId = ZoneId.of("UTC")
        val source: IClockSourceImpl = createSource(currentZone = { inputZone })

        turbineScope {
            val (firstTurbine, secondTurbine) = createCollectors(testScope = this@runTest, source = source)
            val actualReading: ClockReading = awaitSharedReading(firstTurbine, secondTurbine) {
                inputZone = expectedZone
                application.sendBroadcast(Intent(Intent.ACTION_TIMEZONE_CHANGED))
            }

            assertEquals(expectedZone, actualReading.zoneId)
            firstTurbine.cancelAndIgnoreRemainingEvents()
            secondTurbine.cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updates every collector when the locale changes`() = runTest {
        val expectedLocale: Locale = Locale.GERMANY
        var inputLocale: Locale = Locale.US
        val source: IClockSourceImpl = createSource(currentLocale = { inputLocale })

        turbineScope {
            val (firstTurbine, secondTurbine) = createCollectors(testScope = this@runTest, source = source)
            val actualReading: ClockReading = awaitSharedReading(firstTurbine, secondTurbine) {
                inputLocale = expectedLocale
                application.sendBroadcast(Intent(Intent.ACTION_LOCALE_CHANGED))
            }

            assertEquals(expectedLocale, actualReading.locale)
            firstTurbine.cancelAndIgnoreRemainingEvents()
            secondTurbine.cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updates every collector when the 12-24-hour format changes`() = runTest {
        val source: IClockSourceImpl = createSource()

        turbineScope {
            val (firstTurbine, secondTurbine) = createCollectors(testScope = this@runTest, source = source)
            val actualReading: ClockReading = awaitSharedReading(firstTurbine, secondTurbine) {
                Settings.System.putString(application.contentResolver, Settings.System.TIME_12_24, "24")
            }

            assertTrue(actualReading.isSystem24Hour)
            firstTurbine.cancelAndIgnoreRemainingEvents()
            secondTurbine.cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `emits one reading per minute to every collector`() = runTest {
        val source: IClockSourceImpl = createSource()

        turbineScope {
            val (firstTurbine, secondTurbine) = createCollectors(testScope = this@runTest, source = source)
            advanceTimeBy(MILLIS_PER_MINUTE + 1)
            runCurrent()

            firstTurbine.awaitItem()
            secondTurbine.awaitItem()
            firstTurbine.expectNoEvents()
            secondTurbine.expectNoEvents()

            firstTurbine.cancelAndIgnoreRemainingEvents()
            secondTurbine.cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `unregisters the receiver and observer when the last collector leaves`() = runTest {
        val source: IClockSourceImpl = createSource()

        turbineScope {
            val (firstTurbine, secondTurbine) = createCollectors(testScope = this@runTest, source = source)
            assertClockRegistrations(expectedReceiverCount = 1, expectedObserverCount = 1)

            firstTurbine.cancelAndIgnoreRemainingEvents()
            runCurrent()
            assertClockRegistrations(expectedReceiverCount = 1, expectedObserverCount = 1)

            secondTurbine.cancelAndIgnoreRemainingEvents()
            runCurrent()
            assertClockRegistrations(expectedReceiverCount = 1, expectedObserverCount = 1)

            advanceTimeBy(WHILE_SUBSCRIBED_TIMEOUT_MS + 1)
            runCurrent()
            assertClockRegistrations(expectedReceiverCount = 0, expectedObserverCount = 0)
        }
    }

    @Test
    fun `registers again for a collector joining after the shared stream stops`() = runTest {
        val expectedFirstInstant: Instant = Instant.ofEpochMilli(TEST_NOW_EPOCH_MS)
        val expectedNextInstant: Instant = Instant.ofEpochMilli(TEST_NOW_EPOCH_MS + MILLIS_PER_MINUTE)
        var inputInstant: Instant = expectedFirstInstant
        val source: IClockSourceImpl = createSource(currentInstant = { inputInstant })

        source.readings.test {
            runCurrent()
            val actualFirstReading: ClockReading = awaitItem()
            assertEquals(expectedFirstInstant, actualFirstReading.instant)
            cancelAndIgnoreRemainingEvents()
        }
        runCurrent()
        advanceTimeBy(WHILE_SUBSCRIBED_TIMEOUT_MS + 1)
        runCurrent()
        assertClockRegistrations(expectedReceiverCount = 0, expectedObserverCount = 0)

        inputInstant = expectedNextInstant
        source.readings.test {
            runCurrent()
            val actualNextReading: ClockReading = awaitItem()
            assertEquals(expectedNextInstant, actualNextReading.instant)
            runCurrent()
            assertClockRegistrations(expectedReceiverCount = 1, expectedObserverCount = 1)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun TestScope.createSource(
        currentInstant: () -> Instant = { Instant.ofEpochMilli(TEST_NOW_EPOCH_MS) },
        currentZone: () -> ZoneId = { ZoneId.of("UTC") },
        currentLocale: () -> Locale = { Locale.US }
    ): IClockSourceImpl = IClockSourceImpl(
        context = application,
        scope = backgroundScope,
        currentInstant = currentInstant,
        currentZone = currentZone,
        currentLocale = currentLocale
    )

    private suspend fun TurbineContext.createCollectors(
        testScope: TestScope,
        source: IClockSourceImpl
    ): Pair<ReceiveTurbine<ClockReading>, ReceiveTurbine<ClockReading>> {
        val firstTurbine: ReceiveTurbine<ClockReading> = source.readings.testIn(testScope.backgroundScope)
        testScope.runCurrent()
        firstTurbine.awaitItem()
        testScope.runCurrent()
        val secondTurbine: ReceiveTurbine<ClockReading> = source.readings.testIn(testScope.backgroundScope)
        testScope.runCurrent()
        secondTurbine.awaitItem()
        testScope.runCurrent()
        return firstTurbine to secondTurbine
    }

    private suspend fun TestScope.awaitSharedReading(
        firstTurbine: ReceiveTurbine<ClockReading>,
        secondTurbine: ReceiveTurbine<ClockReading>,
        change: () -> Unit
    ): ClockReading {
        change()
        shadowOf(Looper.getMainLooper()).idle()
        runCurrent()
        val actualFirstReading: ClockReading = firstTurbine.awaitItem()
        val actualSecondReading: ClockReading = secondTurbine.awaitItem()
        assertEquals(actualFirstReading, actualSecondReading)
        return actualFirstReading
    }

    private fun assertClockRegistrations(expectedReceiverCount: Int, expectedObserverCount: Int) {
        assertEquals(expectedReceiverCount, timeReceiverCount())
        assertEquals(expectedObserverCount, timeFormatObserverCount())
    }

    private fun timeReceiverCount(): Int =
        shadowOf(application).registeredReceivers.count { wrapper -> wrapper.intentFilter.hasAction(Intent.ACTION_TIME_TICK) }

    private fun timeFormatObserverCount(): Int =
        shadowOf(application.contentResolver).getContentObservers(Settings.System.getUriFor(Settings.System.TIME_12_24)).size
}
