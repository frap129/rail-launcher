package core.lifecycle

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.core.app.TaskStackBuilder
import androidx.lifecycle.lifecycleScope
import core.data.profiles.IProfileSource
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

open class TemplateActivity : ComponentActivity() {
    private val profileSource: IProfileSource by inject()

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch { profileSource.refreshProfiles() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        // Resend intent updates as pending intents to fix navigation
        val data: Uri? = intent.data
        if (data?.scheme == "rail-launcher") {
            val pending: PendingIntent? = TaskStackBuilder.create(this).run {
                addNextIntentWithParentStack(intent)
                getPendingIntent(
                    0,
                    PendingIntent.FLAG_UPDATE_CURRENT.or(PendingIntent.FLAG_IMMUTABLE)
                )
            }
            pending?.send()
        }
    }
}
