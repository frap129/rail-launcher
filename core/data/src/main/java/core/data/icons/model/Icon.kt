package core.data.icons.model

import android.content.ComponentName

sealed class Icon {
    data class PackIcon(val componentName: ComponentName, val packPackageName: String, val resId: Int) : Icon()
    data class ActivityIcon(val componentName: ComponentName) : Icon()
    data class ApplicationIcon(val packageName: String) : Icon()
}
