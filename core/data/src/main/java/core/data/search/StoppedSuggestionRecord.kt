package core.data.search

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A Launchable key the user chose Stop suggesting for, which Home suggestions leave out until reset. */
@Entity(tableName = StoppedSuggestionRecord.TABLE_NAME)
data class StoppedSuggestionRecord(@PrimaryKey val key: String) {
    companion object {
        const val TABLE_NAME: String = "stopped_suggestions"
    }
}
