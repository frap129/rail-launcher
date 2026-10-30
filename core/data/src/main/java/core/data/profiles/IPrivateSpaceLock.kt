package core.data.profiles

import kotlinx.coroutines.flow.StateFlow

/**
 * The Lock Rail has requested for its Private Space before the system confirms or refuses it.
 * While a Lock is requested, Rail treats the Private Space as Locked.
 */
interface IPrivateSpaceLock {
    /** Whether a Lock is requested and the system has not settled it yet. */
    val isLockRequested: StateFlow<Boolean>

    /** Treats the Private Space as Locked until the request settles. */
    fun requestLock()

    /** Stops treating the Private Space as Locked, for example when the system refuses the Lock. */
    fun clearLockRequest()
}
