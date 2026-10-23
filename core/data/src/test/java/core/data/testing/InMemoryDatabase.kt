package core.data.testing

import androidx.room.Room
import core.data.room.AppDatabase
import org.robolectric.RuntimeEnvironment

fun createInMemoryDatabase(): AppDatabase = Room.inMemoryDatabaseBuilder(
    RuntimeEnvironment.getApplication(),
    AppDatabase::class.java
)
    .allowMainThreadQueries()
    .build()
