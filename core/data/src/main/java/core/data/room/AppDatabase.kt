package core.data.room

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import core.data.favorites.FavoriteRecord
import core.data.folders.FolderRecord
import core.data.icons.model.Icon
import core.data.icons.model.IconPack
import core.data.popups.PopupHiddenShortcut
import core.data.popups.PopupItemRecord
import core.data.prefs.EventSettings
import core.data.prefs.HeaderSettings
import core.data.prefs.LaunchableCustomization
import core.data.prefs.Settings
import core.data.room.dao.CustomIconPackDao
import core.data.room.dao.EventSettingsDao
import core.data.room.dao.FavoriteDao
import core.data.room.dao.FolderDao
import core.data.room.dao.HeaderSettingsDao
import core.data.room.dao.LaunchableCustomizationDao
import core.data.room.dao.MediaPlayerSettingsDao
import core.data.room.dao.PackIconDao
import core.data.room.dao.PendingAppWidgetDao
import core.data.room.dao.PopupDao
import core.data.room.dao.SettingsDao
import core.data.room.dao.WidgetAreaDao
import core.data.room.dao.WidgetAreaSettingsDao
import core.data.widgets.MediaPlayerSettings
import core.data.widgets.PendingAppWidgetRecord
import core.data.widgets.WidgetAreaSettingsRecord
import core.data.widgets.WidgetPlaceRecord
import core.data.widgets.WidgetRecord

@Database(
    entities = [
        IconPack.CustomIconPack::class,
        Icon.PackIcon::class,
        LaunchableCustomization::class,
        Settings::class,
        HeaderSettings::class,
        EventSettings::class,
        FavoriteRecord::class,
        WidgetPlaceRecord::class,
        WidgetRecord::class,
        WidgetAreaSettingsRecord::class,
        MediaPlayerSettings::class,
        PendingAppWidgetRecord::class,
        PopupHiddenShortcut::class,
        PopupItemRecord::class,
        FolderRecord::class
    ],
    version = 9
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun getCachedIconPackDao(): CustomIconPackDao
    abstract fun getPackIconDao(): PackIconDao
    abstract fun getLaunchableCustomizationDao(): LaunchableCustomizationDao
    abstract fun getSettingsDao(): SettingsDao
    abstract fun getHeaderSettingsDao(): HeaderSettingsDao
    abstract fun getEventSettingsDao(): EventSettingsDao
    abstract fun getFavoriteDao(): FavoriteDao
    abstract fun getWidgetAreaDao(): WidgetAreaDao
    abstract fun getWidgetAreaSettingsDao(): WidgetAreaSettingsDao
    abstract fun getMediaPlayerSettingsDao(): MediaPlayerSettingsDao
    abstract fun getPendingAppWidgetDao(): PendingAppWidgetDao
    abstract fun getPopupDao(): PopupDao
    abstract fun getFolderDao(): FolderDao
}
