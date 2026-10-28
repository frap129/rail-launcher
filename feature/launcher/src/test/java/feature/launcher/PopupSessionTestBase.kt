package feature.launcher

import android.content.ComponentName
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.SavedStateHandle
import core.data.apps.model.App
import core.data.launchables.model.Launchable
import core.data.notifications.INotificationListenerRelayImpl
import core.data.notifications.INotificationSource
import core.data.notifications.NotificationRecord
import core.data.testing.FakeAppWidgetHostSource
import core.data.testing.FakeClockSource
import core.data.testing.FakeRemoveAppWidget
import core.data.testing.FakeWidgetAreaDao
import core.data.testing.FakeWidgetAreaRepository
import core.data.testing.FakeWidgetSettingsRepository
import core.data.testing.TEST_NOTIFICATION_OTHER_KEY
import core.data.testing.createNotificationRecord
import core.data.testing.createOtherNotificationProfile
import core.data.time.ClockReading
import core.data.time.IClockSource
import core.data.widgets.IRemoveAppWidget
import core.data.widgets.IWidgetLifecycleReconcilerImpl
import core.ui.layout.HomeEditState
import core.ui.popups.IPopupCollaborators
import core.ui.popups.IPopupEditor
import core.ui.popups.IPopupFolderActions
import core.ui.popups.IPopupPicker
import core.ui.popups.IPopupSession
import core.ui.popups.IPopupSessionImpl
import core.ui.popups.PopupTestBase
import feature.launcher.widgets.IWidgetResizeControllerImpl
import feature.launcher.widgets.createAddController
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent

internal const val TEST_WORK_PROFILE_SERIAL: Long = 42L
internal val TEST_NOW: Instant = Instant.ofEpochMilli(10_000_000L)
internal val TEST_INPUT_ANCHOR: PopupAnchor = PopupAnchor(rowTop = 400, rowBottom = 464, iconCenter = Offset(32f, 432f))

/** The card, editor, pickers and Folder actions connected by one Pop-up session. */
class PopupTestSession(
    val session: IPopupSession,
    val card: PopupCardViewModel,
    val editor: IPopupEditor,
    val picker: IPopupPicker,
    val folder: IPopupFolderActions
)

/** The fixtures the launcher Pop-up tests build their card and session from. */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class PopupSessionTestBase : PopupTestBase() {
    protected val inputOtherNotification: NotificationRecord = createNotificationRecord(
        key = TEST_NOTIFICATION_OTHER_KEY,
        packageName = inputOtherApp.packageName
    )
    protected val inputWorkApp: App = App(
        defaultName = "Work",
        profile = createOtherNotificationProfile(),
        componentName = ComponentName("com.example.work", "com.example.work.MainActivity")
    )
    protected val inputSavedStateHandle: SavedStateHandle = SavedStateHandle()
    protected val mockAppWidgetHost: FakeAppWidgetHostSource = FakeAppWidgetHostSource()
    protected val mockRemoveAppWidget: FakeRemoveAppWidget = FakeRemoveAppWidget()
    protected val mockWidgetSettingsRepository: FakeWidgetSettingsRepository = FakeWidgetSettingsRepository()
    protected val mockWidgetAreaRepository: FakeWidgetAreaRepository = FakeWidgetAreaRepository()
    protected val mockNotificationRelay: INotificationListenerRelayImpl = INotificationListenerRelayImpl()

    protected fun TestScope.createCard(
        session: IPopupSession = IPopupSessionImpl(),
        homeEditState: HomeEditState = inputHomeEditState,
        savedStateHandle: SavedStateHandle = inputSavedStateHandle,
        widgetRepository: FakeWidgetAreaRepository = mockWidgetAreaRepository,
        appWidgetHost: FakeAppWidgetHostSource = mockAppWidgetHost,
        removeAppWidget: IRemoveAppWidget = mockRemoveAppWidget,
        widgetSettingsRepository: FakeWidgetSettingsRepository = mockWidgetSettingsRepository,
        notificationSource: INotificationSource = createNotificationSource(),
        clockSource: IClockSource = FakeClockSource(initialReading = clockReading(instant = TEST_NOW)),
        collaborators: IPopupCollaborators = createCollaborators(session = session, homeEditState = homeEditState)
    ): PopupCardViewModel = PopupCardViewModel(
        launchableRepository = mockLaunchableRepository,
        shortcutSource = mockShortcutSource,
        popupRepository = mockPopupRepository,
        notificationSource = notificationSource,
        clockSource = clockSource,
        homeEditState = homeEditState,
        widgetRepository = widgetRepository,
        addFlowController = createAddController(
            repository = widgetRepository,
            appWidgetHost = appWidgetHost,
            homeEditState = homeEditState,
            savedStateHandle = savedStateHandle,
            settingsRepository = widgetSettingsRepository
        ),
        resizeController = IWidgetResizeControllerImpl(
            repository = widgetRepository,
            appWidgetHost = appWidgetHost,
            settingsRepository = widgetSettingsRepository,
            homeEditState = homeEditState
        ),
        appWidgetHost = appWidgetHost,
        removeAppWidget = removeAppWidget,
        widgetLifecycleReconciler = IWidgetLifecycleReconcilerImpl(dao = FakeWidgetAreaDao(), host = appWidgetHost),
        widgetSettingsRepository = widgetSettingsRepository,
        savedStateHandle = savedStateHandle,
        session = session,
        collaborators = collaborators
    )

    protected fun TestScope.createSession(
        homeEditState: HomeEditState = inputHomeEditState,
        notificationSource: INotificationSource = createNotificationSource(),
        clockSource: IClockSource = FakeClockSource(initialReading = clockReading(instant = TEST_NOW))
    ): PopupTestSession {
        val session: IPopupSession = IPopupSessionImpl()
        val collaborators: IPopupCollaborators = createCollaborators(session = session, homeEditState = homeEditState)
        val card: PopupCardViewModel = createCard(
            session = session,
            homeEditState = homeEditState,
            notificationSource = notificationSource,
            clockSource = clockSource,
            collaborators = collaborators
        )
        return PopupTestSession(
            session = session,
            card = card,
            editor = collaborators.editor,
            picker = collaborators.picker,
            folder = collaborators.folder
        )
    }

    /** Starts collecting [flows] and opens [owner]'s Pop-up on [card]. */
    protected fun TestScope.openPopup(card: PopupCardViewModel, owner: Launchable, flows: List<Flow<*>>): PopupCardViewModel {
        flows.forEach { flow -> backgroundScope.launch { flow.collect() } }
        card.open(owner, TEST_INPUT_ANCHOR)
        runCurrent()
        return card
    }

    /** Opens [owner]'s Pop-up and starts editing [editor], seeding [ownerItem] and [editorItem] as added items when given. */
    protected fun TestScope.openPopupWithEditor(
        owner: Launchable = inputApp,
        editor: Launchable = inputFolder,
        ownerItem: Launchable? = inputOtherApp,
        editorItem: Launchable? = inputRailLaunchable
    ): PopupTestSession {
        ownerItem?.let { item -> setPopupLaunchables(ownerKey = owner.key, launchables = listOf(item)) }
        editorItem?.let { item -> setPopupLaunchables(ownerKey = editor.key, launchables = listOf(item)) }
        val session: PopupTestSession = createSession()
        openPopup(
            card = session.card,
            owner = owner,
            flows = listOf(session.card.popupState, session.editor.editState, session.card.menuState)
        )
        session.editor.startEdit(editor)
        runCurrent()
        return session
    }

    protected fun TestScope.createNotificationSource(accessCheck: () -> Boolean = { true }): INotificationSource =
        createTestNotificationSource(
            scope = backgroundScope,
            popupRepository = mockPopupRepository,
            relay = mockNotificationRelay,
            isAccessGranted = accessCheck
        )

    protected fun clockReading(instant: Instant): ClockReading = ClockReading(
        instant = instant,
        zoneId = ZoneId.of("UTC"),
        locale = Locale.US,
        isSystem24Hour = true
    )
}
