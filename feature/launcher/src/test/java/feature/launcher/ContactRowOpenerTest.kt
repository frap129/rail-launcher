package feature.launcher

import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.provider.ContactsContract
import core.data.testing.TEST_CONTACT_ID
import core.data.testing.TEST_CONTACT_LOOKUP_KEY
import core.data.testing.TEST_CONTACT_NUMBER
import core.data.testing.TEST_SDK
import core.data.testing.createSearchContact
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowPackageManager

private const val DIALER_PACKAGE: String = "com.example.dialer"
private const val DIALER_ACTIVITY: String = "com.example.dialer.DialerActivity"
private const val DIAL_SCHEME: String = "tel"

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [TEST_SDK])
class ContactRowOpenerTest : IntentLaunchTestBase() {
    @Test
    fun `builds a dial intent for the contact's number`() {
        val actualIntent: Intent = requireNotNull(
            contactRowIntent(contact = createSearchContact(), action = ContactRowAction.Dial)
        )

        assertEquals(Intent.ACTION_DIAL, actualIntent.action)
        assertEquals(Uri.fromParts("tel", TEST_CONTACT_NUMBER, null), actualIntent.data)
    }

    @Test
    fun `builds a message intent for the contact's number`() {
        val actualIntent: Intent = requireNotNull(
            contactRowIntent(contact = createSearchContact(), action = ContactRowAction.Message)
        )

        assertEquals(Intent.ACTION_SENDTO, actualIntent.action)
        assertEquals(Uri.fromParts("smsto", TEST_CONTACT_NUMBER, null), actualIntent.data)
    }

    @Test
    fun `builds an intent that opens the contact by its lookup key`() {
        val actualIntent: Intent = requireNotNull(
            contactRowIntent(contact = createSearchContact(), action = ContactRowAction.Open)
        )

        assertEquals(Intent.ACTION_VIEW, actualIntent.action)
        assertEquals(ContactsContract.Contacts.getLookupUri(TEST_CONTACT_ID, TEST_CONTACT_LOOKUP_KEY), actualIntent.data)
    }

    @Test
    fun `builds no call or message intent for a contact without a number`() {
        val inputContact = createSearchContact(phoneNumber = null)

        assertNull(contactRowIntent(contact = inputContact, action = ContactRowAction.Dial))
        assertNull(contactRowIntent(contact = inputContact, action = ContactRowAction.Message))
    }

    @Test
    fun `opens the dialer with the contact's number`() {
        registerDialer()
        val actualOpened: Boolean = openContactRow(
            context = context,
            contact = createSearchContact(),
            action = ContactRowAction.Dial
        )

        val actualIntent: Intent? = shadowActivity.nextStartedActivity
        assertTrue(actualOpened)
        assertEquals(Intent.ACTION_DIAL, actualIntent?.action)
        assertEquals(Uri.fromParts("tel", TEST_CONTACT_NUMBER, null), actualIntent?.data)
    }

    @Test
    fun `reports a call that cannot open for a contact without a number`() {
        val actualOpened: Boolean = openContactRow(
            context = context,
            contact = createSearchContact(phoneNumber = null),
            action = ContactRowAction.Dial
        )

        assertFalse(actualOpened)
        assertNull(shadowActivity.nextStartedActivity)
    }

    @Test
    fun `reports an action that no app can handle`() {
        val actualOpened: Boolean = openContactRow(
            context = context,
            contact = createSearchContact(),
            action = ContactRowAction.Dial
        )

        assertFalse(actualOpened)
        assertNull(shadowActivity.nextStartedActivity)
    }

    private fun registerDialer() {
        val manager: ShadowPackageManager = shadowOf(context.packageManager)
        val component: ComponentName = ComponentName(DIALER_PACKAGE, DIALER_ACTIVITY)
        manager.addActivityIfNotPresent(component)
        manager.addIntentFilterForActivity(
            component,
            IntentFilter(Intent.ACTION_DIAL).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                addDataScheme(DIAL_SCHEME)
            }
        )
    }
}
