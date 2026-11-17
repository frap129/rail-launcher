package core.data.testing

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.CoroutineDispatcher

/** An in-memory [T] with main-thread queries on [queryContext], after [configure] has changed the builder. */
inline fun <reified T : RoomDatabase> createInMemoryDatabase(
    context: Context,
    queryContext: CoroutineDispatcher,
    configure: (RoomDatabase.Builder<T>) -> RoomDatabase.Builder<T> = { builder -> builder }
): T = configure(Room.inMemoryDatabaseBuilder(context, T::class.java))
    .configureForTest(queryContext = queryContext)
    .build()

/** Gives [this] builder the main-thread access and test-controlled execution the database tests expect. */
fun <T : RoomDatabase> RoomDatabase.Builder<T>.configureForTest(queryContext: CoroutineDispatcher): RoomDatabase.Builder<T> =
    allowMainThreadQueries().setQueryCoroutineContext(queryContext)
