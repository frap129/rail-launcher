package feature.launcher

import android.content.Context
import android.widget.Toast

class IPinShortcutNotifierImpl(private val context: Context) : IPinShortcutNotifier {
    override fun showAddError() {
        Toast.makeText(context.applicationContext, R.string.couldnt_add_shortcut, Toast.LENGTH_SHORT).show()
    }
}
