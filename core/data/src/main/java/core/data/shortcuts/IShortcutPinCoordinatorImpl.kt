package core.data.shortcuts

import core.data.apps.model.AppProfile
import core.data.launchables.ILaunchableRepository
import core.data.launchables.LaunchableState
import core.data.profiles.ProfileToken
import core.data.profiles.toProfileToken
import core.data.room.TransactionEffects
import core.data.room.dao.PopupDao
import core.data.runCleanup
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serializes every [IShortcutSource.pin] call and reapplies pins after startup, writes and Profile enumeration. */
class IShortcutPinCoordinatorImpl(
    private val shortcutSource: IShortcutSource,
    private val launchableRepository: ILaunchableRepository,
    private val popupDao: PopupDao,
    private val pinSources: List<IShortcutPinSource>,
    scope: CoroutineScope
) : IShortcutPinCoordinator {
    private val pinMutex: Mutex = Mutex()

    init {
        scope.launch {
            runCleanup(message = "Failed to reapply Shortcut pins on startup") { reapplyAllPins() }
        }
        scope.launch {
            launchableRepository.launchableState
                .filter { state -> state.isLoaded }
                .map { state -> state.enumeratedKeys }
                .distinctUntilChanged()
                .collect { tokens ->
                    runCleanup(message = "Failed to reapply Shortcut pins after Profile enumeration") {
                        reapplyPinsForProfiles(tokens = tokens)
                    }
                }
        }
    }

    override suspend fun reapplyPins(affectedProfiles: Set<AppProfile>) {
        if (affectedProfiles.isEmpty()) {
            return
        }
        val effects: TransactionEffects? = coroutineContext[TransactionEffects.Key]
        if (effects == null) {
            withPinLock { pinProfiles(affectedProfiles = affectedProfiles, pinSets = currentPinSets()) }
        } else {
            // Releasing a system pin cannot be undone, so it waits for the caller's commit.
            effects.afterCommit { withPinLock { pinProfiles(affectedProfiles = affectedProfiles, pinSets = currentPinSets()) } }
        }
    }

    /** Reapplies every merged pin set once the Launchables load, skipping Kept Profiles. */
    private suspend fun reapplyAllPins() {
        val state: LaunchableState = launchableRepository.launchableState.filter { current -> current.isLoaded }.first()
        reapplyPinsWhere { profile -> profile.profile.toProfileToken() !in state.keptKeys }
    }

    /** Reapplies pins once a Profile that was Locked lists its Apps again. */
    private suspend fun reapplyPinsForProfiles(tokens: Set<ProfileToken>) {
        if (tokens.isEmpty()) {
            return
        }
        reapplyPinsWhere { profile -> profile.profile.toProfileToken() in tokens }
    }

    private suspend fun reapplyPinsWhere(predicate: (AppProfile) -> Boolean) {
        withPinLock {
            val pinSets: Map<AppProfile, Set<String>> = currentPinSets()
            pinProfiles(
                affectedProfiles = pinSets.keys.filterTo(mutableSetOf(), predicate),
                pinSets = pinSets
            )
        }
    }

    private suspend fun pinProfiles(affectedProfiles: Set<AppProfile>, pinSets: Map<AppProfile, Set<String>>) {
        for (profile: AppProfile in affectedProfiles) {
            shortcutSource.pin(
                packageName = profile.packageName,
                profile = profile.profile,
                ids = pinSets[profile].orEmpty()
            )
        }
    }

    /** The union of the Pop-up Shortcut items and every other source, so adding a source needs no pin-path change. */
    private suspend fun currentPinSets(): Map<AppProfile, Set<String>> {
        val merged: MutableMap<AppProfile, MutableSet<String>> = mutableMapOf()
        mergePinSets(into = merged, pinSets = popupPinSets())
        pinSources.forEach { source -> mergePinSets(into = merged, pinSets = source.pinSets()) }
        return merged.mapValues { (_, ids) -> ids.toSet() }
    }

    private suspend fun popupPinSets(): Map<AppProfile, Set<String>> = popupDao.getAllItems()
        .mapNotNull { record -> ShortcutKey.parse(record.itemKey) }
        .groupBy({ key -> AppProfile(packageName = key.packageName, profile = key.profile) }, { key -> key.id })
        .mapValues { (_, ids) -> ids.toSet() }

    private fun mergePinSets(into: MutableMap<AppProfile, MutableSet<String>>, pinSets: Map<AppProfile, Set<String>>) {
        pinSets.forEach { (profile: AppProfile, ids: Set<String>) ->
            into.getOrPut(profile) { mutableSetOf() }.addAll(ids)
        }
    }

    private suspend fun <T> withPinLock(block: suspend () -> T): T = pinMutex.withLock { block() }
}
