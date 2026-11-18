package core.data.apps

import core.data.apps.model.App
import core.data.profiles.ProfileKind
import core.data.room.dao.LastKnownAppDao

class ILastKnownAppStoreImpl(private val lastKnownAppDao: LastKnownAppDao) : ILastKnownAppStore {
    override suspend fun save(apps: List<App>) {
        val records: List<LastKnownAppRecord> = apps
            .filter { app -> app.profileInfo.kind.isKnownNotPrivate }
            .map { app -> app.toRecord() }
        lastKnownAppDao.replace(records = records)
    }

    override suspend fun read(): List<LastKnownAppRecord> = lastKnownAppDao.getAll()
}

// An Other Profile's kind is unknown, so an Unlocked Private Space whose user info failed to read would look Other.
private val ProfileKind.isKnownNotPrivate: Boolean
    get() = this == ProfileKind.Personal || this == ProfileKind.Work
