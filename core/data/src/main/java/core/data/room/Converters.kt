package core.data.room

import android.content.ComponentName
import androidx.room.TypeConverter
import core.data.icons.model.Icon
import core.data.widgets.WidgetKind

class Converters {
    @TypeConverter
    fun fromComponentNameToString(componentName: ComponentName): String = componentName.flattenToString()

    @TypeConverter
    fun fromStringToComponentName(componentName: String): ComponentName = ComponentName.unflattenFromString(componentName)!!

    @TypeConverter
    fun fromApplicationIconToString(appIcon: Icon.ApplicationIcon): String = appIcon.packageName

    @TypeConverter
    fun fromStringToApplicationIcon(packageName: String): Icon.ApplicationIcon = Icon.ApplicationIcon(packageName)

    @TypeConverter
    fun fromWidgetKindToString(kind: WidgetKind): String = kind.name

    @TypeConverter
    fun fromStringToWidgetKind(kind: String): WidgetKind =
        WidgetKind.entries.firstOrNull { entry -> entry.name == kind } ?: WidgetKind.UNKNOWN
}
