package feature.launcher

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import core.data.search.SearchContact
import core.util.tryStartActivity

/** What a Contact row's tap or one of its buttons does. */
internal enum class ContactRowAction {
    Open,
    Dial,
    Message
}

/**
 * The intent that performs [action] for [contact], or null when the contact has no number for a
 * call or message. Opening uses the lookup key, so it still resolves after the contact changes.
 */
internal fun contactRowIntent(contact: SearchContact, action: ContactRowAction): Intent? = when (action) {
    ContactRowAction.Open -> Intent(
        Intent.ACTION_VIEW,
        ContactsContract.Contacts.getLookupUri(contact.contactId, contact.lookupKey)
    )

    ContactRowAction.Dial -> contact.phoneNumber?.let { number ->
        Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null))
    }

    ContactRowAction.Message -> contact.phoneNumber?.let { number ->
        Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number, null))
    }
}

/** Runs [action] for [contact], returning whether an app accepted the intent. */
internal fun openContactRow(context: Context, contact: SearchContact, action: ContactRowAction): Boolean =
    contactRowIntent(contact = contact, action = action)?.let { intent -> context.tryStartActivity(intent = intent) } ?: false
