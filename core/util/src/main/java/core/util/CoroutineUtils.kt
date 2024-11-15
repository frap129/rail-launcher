package core.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

fun launchInBackground(block: suspend CoroutineScope.() -> Unit) = CoroutineScope(Dispatchers.IO).launch(block = block)

fun <T> Flow<T>.stateInBackground(initialValue: T) = stateIn(CoroutineScope(Dispatchers.IO), SharingStarted.WhileSubscribed(), initialValue)
