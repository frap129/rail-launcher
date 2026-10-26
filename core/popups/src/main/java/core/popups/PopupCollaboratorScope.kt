package core.popups

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/** The child scope a Pop-up collaborator runs in; [release] stops everything it started. */
internal class PopupCollaboratorScope(parentScope: CoroutineScope) {
    val scope: CoroutineScope = CoroutineScope(
        SupervisorJob(parent = parentScope.coroutineContext[Job]) + Dispatchers.Main.immediate
    )

    /** Stops the collaborator's flows and writes. */
    fun release() {
        scope.cancel()
    }
}
