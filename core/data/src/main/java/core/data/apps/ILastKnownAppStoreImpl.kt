package core.data.apps

import core.data.apps.model.App
import core.data.profiles.isKnownNotPrivate
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
