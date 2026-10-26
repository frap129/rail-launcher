package core.data.testing

import core.data.prefs.LaunchableCustomization
import core.data.room.dao.LaunchableCustomizationDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

internal const val FAKE_WRITE_TIMEOUT_MS: Long = 5_000L

data class LaunchableRename(val key: String, val name: String)

class FakeLaunchableCustomizationDao(inputCustomizations: List<LaunchableCustomization> = emptyList()) : LaunchableCustomizationDao {
    private val customizations: MutableStateFlow<List<LaunchableCustomization>> = MutableStateFlow(inputCustomizations)
    private val renameSignals: Channel<Unit> = Channel(Channel.UNLIMITED)
    private val deleteSignals: Channel<Unit> = Channel(Channel.UNLIMITED)
    private val recordedRenames: MutableList<LaunchableRename> = mutableListOf()
    val renames: List<LaunchableRename> get() = recordedRenames.toList()
    val deletedKeys: MutableList<String> = mutableListOf()

    /** Fails the next [setName] call instead of writing. */
    val failNextSetName: FailOnce = FailOnce()

    override fun observeAll(): Flow<List<LaunchableCustomization>> = customizations

    override suspend fun setName(key: String, name: String) {
        failNextSetName.throwIfPending()
        recordedRenames.add(LaunchableRename(key = key, name = name))
        val existing: LaunchableCustomization? = customizations.value.find { it.key == key }
        val updated: LaunchableCustomization = (existing ?: LaunchableCustomization(key = key)).copy(name = name)
        customizations.value = customizations.value.filterNot { it.key == key } + updated
        renameSignals.send(Unit)
    }

    override suspend fun delete(key: String) {
        deletedKeys += key
        customizations.value = customizations.value.filterNot { customization -> customization.key == key }
        deleteSignals.send(Unit)
    }

    suspend fun awaitRename(): LaunchableRename = withContext(Dispatchers.Default) {
        withTimeout(FAKE_WRITE_TIMEOUT_MS) { renameSignals.receive() }
        recordedRenames.last()
    }

    suspend fun awaitDelete(): Unit = withContext(Dispatchers.Default) {
        withTimeout(FAKE_WRITE_TIMEOUT_MS) { deleteSignals.receive() }
    }
}
