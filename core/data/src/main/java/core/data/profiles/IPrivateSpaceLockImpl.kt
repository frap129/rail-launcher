package core.data.profiles

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber

class IPrivateSpaceLockImpl(profileSource: IProfileSource, scope: CoroutineScope) : IPrivateSpaceLock {
    private val lockRequested: MutableStateFlow<Boolean> = MutableStateFlow(false)

    override val isLockRequested: StateFlow<Boolean> = lockRequested.asStateFlow()

    init {
        scope.launch {
            profileSource.profiles
                .map { profiles -> profiles.privateSpace?.let { profile -> profile.isQuiet } ?: true }
                .distinctUntilChanged()
                .collect { isLocked ->
                    try {
                        if (isLocked) clearLockRequest()
                    } catch (exception: CancellationException) {
                        throw exception
                    } catch (exception: Exception) {
                        Timber.e(exception, "Private Space Lock request observation failed")
                    }
                }
        }
    }

    override fun requestLock() {
        lockRequested.value = true
    }

    override fun clearLockRequest() {
        lockRequested.value = false
    }
}
