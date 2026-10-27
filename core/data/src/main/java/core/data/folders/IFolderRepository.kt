package core.data.folders

import core.data.widgets.AppWidgetId
import kotlinx.coroutines.flow.Flow

interface IFolderRepository {
    /** Every Folder, with its resolved name. */
    val folders: Flow<List<Folder>>

    /**
     * Creates a Folder, stores [name] as its customization when it is not blank or the default,
     * adds [initialItems] to its Pop-up and, when [addToFavorites] is true, appends it to the Favorites.
     */
    suspend fun create(name: String?, initialItems: List<String> = emptyList(), addToFavorites: Boolean = true): Folder

    /** Deletes [folder] and everything belonging to it, returning the App Widget ids it held. */
    suspend fun delete(folder: Folder): List<AppWidgetId>
}
