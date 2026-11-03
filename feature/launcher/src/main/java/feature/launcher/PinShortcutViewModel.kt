package feature.launcher

import androidx.lifecycle.ViewModel
import core.data.shortcuts.IStandaloneShortcutRepository
import core.data.shortcuts.ShortcutKey
import core.util.logTypeName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/** The outcome of an Add tap in the Pin request dialog. */
sealed interface PinShortcutAddState {
    data object Idle : PinShortcutAddState
    data object Adding : PinShortcutAddState
    data object Added : PinShortcutAddState
    data object Failed : PinShortcutAddState

    val isFinished: Boolean
        get() = this == Added || this == Failed

    val isAdding: Boolean
        get() = this == Adding
}

/**
 * Runs Pin request adds in the application scope so recreating the dialog cannot cancel them.
 * The add's failure is shown from the application context even when the dialog is gone.
 */
class PinShortcutViewModel(
    private val standaloneShortcutRepository: IStandaloneShortcutRepository,
    private val scope: CoroutineScope,
    private val notifier: IPinShortcutNotifier
) : ViewModel() {
    private val mutableAddState: MutableStateFlow<PinShortcutAddState> = MutableStateFlow(PinShortcutAddState.Idle)
    val addState: StateFlow<PinShortcutAddState> = mutableAddState.asStateFlow()

    fun add(key: ShortcutKey, accept: suspend () -> Boolean) {
        if (!mutableAddState.compareAndSet(PinShortcutAddState.Idle, PinShortcutAddState.Adding)) {
            return
        }
        scope.launch {
            val isAdded: Boolean = try {
                standaloneShortcutRepository.add(key = key, accept = accept)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Timber.w("Could not add a Shortcut pin request: %s", exception.logTypeName())
                false
            }
            withContext(Dispatchers.Main) {
                if (!isAdded) {
                    notifier.showAddError()
                }
                mutableAddState.value = if (isAdded) PinShortcutAddState.Added else PinShortcutAddState.Failed
            }
        }
    }
}
