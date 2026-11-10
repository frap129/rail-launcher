package core.ui.model.data

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LauncherSignalRequestTest {
    @Test
    fun `round-trips every launcher signal`() = runTest {
        val inputSignals: List<LauncherSignal> = listOf(
            LauncherSignal.Home(wasResumed = true),
            LauncherSignal.Home(wasResumed = false),
            LauncherSignal.OpenSearch,
            LauncherSignal.TypedCharacter(character = "a")
        )
        val savedStateHandle: SavedStateHandle = SavedStateHandle()
        val launcherSignalRequest: LauncherSignalRequest = LauncherSignalRequest(savedStateHandle)
        val actualSignals: MutableList<LauncherSignal> = mutableListOf()
        backgroundScope.launch { launcherSignalRequest.collectRequests { signal -> actualSignals += signal } }
        runCurrent()

        for (inputSignal in inputSignals) {
            launcherSignalRequest.request(signal = inputSignal)
            runCurrent()
        }

        assertEquals(inputSignals, actualSignals)
    }

    @Test
    fun `signals requested before the collector runs are all delivered in order`() = runTest {
        val inputSignals: List<LauncherSignal> = listOf(
            LauncherSignal.TypedCharacter(character = "c"),
            LauncherSignal.TypedCharacter(character = "a"),
            LauncherSignal.TypedCharacter(character = "l"),
            LauncherSignal.OpenSearch,
            LauncherSignal.Home(wasResumed = true)
        )
        val savedStateHandle: SavedStateHandle = SavedStateHandle()
        val launcherSignalWriter: LauncherSignalRequest = LauncherSignalRequest(savedStateHandle)
        val actualSignals: MutableList<LauncherSignal> = mutableListOf()
        inputSignals.forEach { inputSignal -> launcherSignalWriter.request(signal = inputSignal) }

        val launcherSignalReader: LauncherSignalRequest = LauncherSignalRequest(savedStateHandle)
        backgroundScope.launch { launcherSignalReader.collectRequests { signal -> actualSignals += signal } }
        runCurrent()

        assertEquals(inputSignals, actualSignals)
    }

    @Test
    fun `pending signals survive a recreated request`() = runTest {
        val savedStateHandle: SavedStateHandle = SavedStateHandle()
        LauncherSignalRequest(savedStateHandle).request(signal = LauncherSignal.OpenSearch)
        val launcherSignalRequest: LauncherSignalRequest = LauncherSignalRequest(savedStateHandle)
        val actualSignals: MutableList<LauncherSignal> = mutableListOf()

        backgroundScope.launch { launcherSignalRequest.collectRequests { signal -> actualSignals += signal } }
        runCurrent()

        assertEquals(listOf(LauncherSignal.OpenSearch), actualSignals)
    }
}
