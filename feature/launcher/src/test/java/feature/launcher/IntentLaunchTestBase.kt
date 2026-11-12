package feature.launcher

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import org.junit.Before
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowActivity
import org.robolectric.shadows.ShadowApplication
import org.robolectric.shadows.ShadowPackageManager

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

    /** Registers an Activity that handles [action] for [dataScheme], with the default category and any extra [categories]. */
    protected fun registerIntentHandler(
        packageName: String,
        className: String,
        action: String,
        dataScheme: String,
        categories: List<String> = emptyList()
    ) {
        val manager: ShadowPackageManager = shadowOf(context.packageManager)
        val component: ComponentName = ComponentName(packageName, className)
        manager.addActivityIfNotPresent(component)
        manager.addIntentFilterForActivity(
            component,
            IntentFilter(action).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                categories.forEach { category -> addCategory(category) }
                addDataScheme(dataScheme)
            }
        )
    }
}
