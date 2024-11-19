package core.data.icons.model

import android.content.ComponentName
import androidx.room.Entity

sealed class Icon {
    data class ActivityIcon(val componentName: ComponentName) : Icon()
    data class ApplicationIcon(val packageName: String) : Icon()

    @Entity(tableName = "icons", primaryKeys = ["componentName", "packPackageName"])
    data class PackIcon(val componentName: ComponentName, val packPackageName: String, val resId: Int) : Icon()
}
