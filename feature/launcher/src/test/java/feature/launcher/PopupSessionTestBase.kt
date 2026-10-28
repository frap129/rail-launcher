package feature.launcher

import android.content.ComponentName
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.SavedStateHandle
import core.data.apps.model.App
import core.data.launchables.model.Launchable
import core.data.notifications.INotificationListenerRelayImpl
import core.data.notifications.INotificationSource
import core.data.notifications.NotificationRecord
import core.data.testing.AppWidgetHostState
import core.data.testing.TEST_NOTIFICATION_OTHER_KEY
import core.data.testing.createAppWidgetHostMock
import core.data.testing.createNotificationRecord
import core.data.testing.createOtherNotificationProfile
import core.data.testing.createRemoveAppWidgetMock
import core.data.testing.createTestNotificationSource
import core.data.testing.createWidgetAreaRepositoryMock
import core.data.testing.createWidgetLifecycleReconcilerMock
import core.data.testing.createWidgetSettingsRepositoryMock
import core.data.time.ClockReading
import core.data.time.IClockSource
import core.data.widgets.IAppWidgetHostSource
import core.data.widgets.IRemoveAppWidget
import core.data.widgets.IWidgetAreaRepository
import core.data.widgets.IWidgetSettingsRepository
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
import feature.launcher.widgets.createClock
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import org.robolectric.RuntimeEnvironment

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
    protected val mockWidgetAreaRepository: IWidgetAreaRepository = createWidgetAreaRepositoryMock()
    protected val inputAppWidgetHostState: AppWidgetHostState = AppWidgetHostState()
    protected val mockAppWidgetHost: IAppWidgetHostSource = createAppWidgetHostMock(inputAppWidgetHostState)
    protected val mockRemoveAppWidget: IRemoveAppWidget = createRemoveAppWidgetMock()
    protected val mockWidgetSettingsRepository: IWidgetSettingsRepository = createWidgetSettingsRepositoryMock()
    protected val notificationRelay: INotificationListenerRelayImpl = INotificationListenerRelayImpl()
    protected val mockClockSource: IClockSource = createClock(clockReading(instant = TEST_NOW))

    protected fun TestScope.createCard(
        session: IPopupSession = IPopupSessionImpl(),
        homeEditState: HomeEditState = inputHomeEditState,
        savedStateHandle: SavedStateHandle = inputSavedStateHandle,
        mockWidgetRepository: IWidgetAreaRepository = mockWidgetAreaRepository,
        mockAppWidgetHost: IAppWidgetHostSource = this@PopupSessionTestBase.mockAppWidgetHost,
        removeAppWidget: IRemoveAppWidget = mockRemoveAppWidget,
        mockWidgetSettingsRepository: IWidgetSettingsRepository = this@PopupSessionTestBase.mockWidgetSettingsRepository,
        notificationSource: INotificationSource = createNotificationSource(),
        mockClockSource: IClockSource = this@PopupSessionTestBase.mockClockSource,
        collaborators: IPopupCollaborators = createCollaborators(session = session, homeEditState = homeEditState)
    ): PopupCardViewModel = PopupCardViewModel(
        launchableRepository = mockLaunchableRepository,
        shortcutSource = mockShortcutSource,
        popupRepository = mockPopupRepository,
        notificationSource = notificationSource,
        clockSource = mockClockSource,
        homeEditState = homeEditState,
        widgetRepository = mockWidgetRepository,
        addFlowController = createAddController(
            mockRepository = mockWidgetRepository,
            mockAppWidgetHost = mockAppWidgetHost,
            homeEditState = homeEditState,
            savedStateHandle = savedStateHandle,
            mockSettingsRepository = mockWidgetSettingsRepository
        ),
        resizeController = IWidgetResizeControllerImpl(
            repository = mockWidgetRepository,
            appWidgetHost = mockAppWidgetHost,
            settingsRepository = mockWidgetSettingsRepository,
            homeEditState = homeEditState
        ),
        appWidgetHost = mockAppWidgetHost,
        removeAppWidget = removeAppWidget,
        widgetLifecycleReconciler = createWidgetLifecycleReconcilerMock(),
        widgetSettingsRepository = mockWidgetSettingsRepository,
        savedStateHandle = savedStateHandle,
        session = session,
        collaborators = collaborators
    )

    protected fun TestScope.createSession(): PopupTestSession {
        val session: IPopupSession = IPopupSessionImpl()
        val collaborators: IPopupCollaborators = createCollaborators(session = session, homeEditState = inputHomeEditState)
        val card: PopupCardViewModel = createCard(
            session = session,
            homeEditState = inputHomeEditState,
            notificationSource = createNotificationSource(),
            mockClockSource = mockClockSource,
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

    /**
     * Opens [owner]'s Pop-up and starts editing [editor], adding [inputOtherApp] to the owner and
     * [editorItem], when given, to the editor.
     */
    protected fun TestScope.openPopupWithEditor(
        owner: Launchable = inputApp,
        editor: Launchable = inputFolder,
        editorItem: Launchable? = inputRailLaunchable
    ): PopupTestSession {
        setPopupLaunchables(ownerKey = owner.key, launchables = listOf(inputOtherApp))
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
            context = RuntimeEnvironment.getApplication(),
            scope = backgroundScope,
            mockPopupRepository = mockPopupRepository,
            relay = notificationRelay,
            isAccessGranted = accessCheck
        )

    protected fun clockReading(instant: Instant): ClockReading = ClockReading(
        instant = instant,
        zoneId = ZoneId.of("UTC"),
        locale = Locale.US,
        isSystem24Hour = true
    )
}
