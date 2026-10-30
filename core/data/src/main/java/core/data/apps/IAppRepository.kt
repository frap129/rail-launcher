package core.data.apps

import android.os.UserHandle
import core.data.apps.model.App
import kotlinx.coroutines.flow.StateFlow

interface IAppRepository {
    /** The installed apps, or null until the initial list has loaded. */
    val apps: StateFlow<List<App>?>

    /**
     * The Profiles whose Apps have been enumerated, including those that listed none. A Locked
     * Private Space is left out because Rail never reads its Apps then.
     */
    val enumeratedProfiles: StateFlow<Set<UserHandle>>
}
