package feature.launcher

import android.app.Activity
import org.junit.Before
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowActivity
import org.robolectric.shadows.ShadowApplication

/** An Activity whose started intents can be inspected, resolving only the handlers a test registers. */
abstract class IntentLaunchTestBase {
    protected val context: Activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    protected val shadowContext: ShadowApplication = shadowOf(RuntimeEnvironment.getApplication())
    protected val shadowActivity: ShadowActivity = shadowOf(context)

    @Before
    fun handleOnlyRegisteredActivities() {
        shadowContext.checkActivities(true)
        shadowContext.clearNextStartedActivities()
    }
}
