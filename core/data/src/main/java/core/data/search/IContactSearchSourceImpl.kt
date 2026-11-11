package core.data.search

import android.Manifest
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import core.data.permissions.PermissionState
import core.data.prefs.IPreferencesRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Reads the current profile's contacts, with each contact's preferred phone number, while Contact
 * results is on and READ_CONTACTS is granted, and nothing otherwise. Contacts of other profiles,
 * such as a Work profile, are never read. Queries run on [queryDispatcher] and are refreshed when
 * ContactsContract changes.
 */
class IContactSearchSourceImpl(
    context: Context,
    private val preferencesRepository: IPreferencesRepository,
    private val queryDispatcher: CoroutineDispatcher = Dispatchers.IO
) : IContactSearchSource {
    private val appContext: Context = context.applicationContext
    private val permissionState: PermissionState = PermissionState(
        context = appContext,
        permission = Manifest.permission.READ_CONTACTS
    )

    override val hasPermission: StateFlow<Boolean> = permissionState.value

    override fun refreshPermission() {
        permissionState.refresh()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override val contacts: Flow<List<SearchContact>> = combine(
        preferencesRepository.settings.map { settings -> settings.searchContacts }.distinctUntilChanged(),
        permissionState.value
    ) { areContactsOn, hasPermission -> canSearchContacts(areContactsOn = areContactsOn, hasPermission = hasPermission) }
        .distinctUntilChanged()
        .flatMapLatest { isAllowed -> if (isAllowed) contactChanges() else flowOf(emptyList()) }
        .catch { exception ->
            if (exception is CancellationException) {
                throw exception
            }
            Timber.e(exception, "Failed to read contacts")
            emit(emptyList())
        }

    // Changes only signal a re-read; the read itself is conflated so a burst of provider changes
    // cannot run several contact queries at once.
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun contactChanges(): Flow<List<SearchContact>> = callbackFlow {
        val observer: ContentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        var isObserverRegistered: Boolean = false
        try {
            contactUris().forEach { uri ->
                appContext.contentResolver.registerContentObserver(uri, true, observer)
                isObserverRegistered = true
            }
        } catch (exception: SecurityException) {
            Timber.e(exception, "Failed to observe contacts")
        }
        trySend(Unit)
        awaitClose {
            if (isObserverRegistered) {
                appContext.contentResolver.unregisterContentObserver(observer)
            }
        }
    }.conflate()
        .mapLatest { queryContacts() }

    private suspend fun queryContacts(): List<SearchContact> = withContext(queryDispatcher) {
        try {
            readContacts()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to read contacts")
            emptyList()
        }
    }

    private fun readContacts(): List<SearchContact> {
        val numbersByContactId: Map<Long, String> = readPreferredNumbers()
        val contacts: MutableList<SearchContact> = mutableListOf()
        appContext.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.LOOKUP_KEY,
                ContactsContract.Contacts.DISPLAY_NAME
            ),
            null,
            null,
            "${ContactsContract.Contacts.DISPLAY_NAME} COLLATE NOCASE ASC"
        )?.use { cursor ->
            val idIndex: Int = cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID)
            val lookupKeyIndex: Int = cursor.getColumnIndexOrThrow(ContactsContract.Contacts.LOOKUP_KEY)
            val nameIndex: Int = cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val contactId: Long = cursor.getLong(idIndex)
                val lookupKey: String = cursor.getString(lookupKeyIndex).orEmpty()
                val name: String = cursor.getString(nameIndex).orEmpty().trim()
                if (name.isNotEmpty() && lookupKey.isNotEmpty()) {
                    contacts += SearchContact(
                        contactId = contactId,
                        lookupKey = lookupKey,
                        name = name,
                        phoneNumber = numbersByContactId[contactId]
                    )
                }
            }
        }
        return contacts
    }

    private fun readPreferredNumbers(): Map<Long, String> {
        val numbersByContactId: MutableMap<Long, MutableList<ContactNumber>> = mutableMapOf()
        appContext.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY,
                ContactsContract.CommonDataKinds.Phone.IS_PRIMARY
            ),
            null,
            null,
            null
        )?.use { cursor ->
            val contactIdIndex: Int = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val numberIndex: Int = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val superPrimaryIndex: Int = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY)
            val primaryIndex: Int = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.IS_PRIMARY)
            while (cursor.moveToNext()) {
                val contactId: Long = cursor.getLong(contactIdIndex)
                val number: String = cursor.getString(numberIndex).orEmpty().trim()
                if (contactId > 0 && number.isNotEmpty()) {
                    numbersByContactId.getOrPut(key = contactId) { mutableListOf() } += ContactNumber(
                        number = number,
                        isSuperPrimary = cursor.getInt(superPrimaryIndex) != 0,
                        isPrimary = cursor.getInt(primaryIndex) != 0
                    )
                }
            }
        }
        return numbersByContactId.mapNotNull { (contactId, numbers) ->
            preferredContactNumber(numbers = numbers)?.let { number -> contactId to number }
        }.toMap()
    }
}

/** Whether Search may read contacts: the Contact results setting is on and the permission is granted. */
internal fun canSearchContacts(areContactsOn: Boolean, hasPermission: Boolean): Boolean = areContactsOn && hasPermission

/** The ContactsContract tables that change when a contact or one of its numbers changes. */
private fun contactUris(): List<Uri> = listOf(
    ContactsContract.Contacts.CONTENT_URI,
    ContactsContract.Data.CONTENT_URI
)
