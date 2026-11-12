package feature.launcher

import org.robolectric.RuntimeEnvironment

/** The string resource [id] as the Robolectric application resolves it. */
internal fun getString(id: Int): String = RuntimeEnvironment.getApplication().getString(id)
