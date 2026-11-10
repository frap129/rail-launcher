package core.data.testing

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import core.data.room.AppDatabase
import kotlinx.coroutines.CoroutineDispatcher

/** An in-memory [AppDatabase] with main-thread queries on [queryContext], after [configure] has changed the builder. */
fun createInMemoryDatabase(
    context: Context,
    queryContext: CoroutineDispatcher,
    configure: (RoomDatabase.Builder<AppDatabase>) -> RoomDatabase.Builder<AppDatabase> = { builder -> builder }
): AppDatabase = configure(Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java))
    .configureForTest(queryContext = queryContext)
    .build()

/** Gives [this] builder the main-thread access and test-controlled execution the database tests expect. */
fun RoomDatabase.Builder<AppDatabase>.configureForTest(queryContext: CoroutineDispatcher): RoomDatabase.Builder<AppDatabase> =
    allowMainThreadQueries().setQueryCoroutineContext(queryContext)
