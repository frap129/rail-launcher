package feature.launcher.onboarding

import android.Manifest
import android.content.Context
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Bell
import compose.icons.fontawesomeicons.solid.Calendar
import compose.icons.fontawesomeicons.solid.Check
import compose.icons.fontawesomeicons.solid.Clock
import compose.icons.fontawesomeicons.solid.HandPointer
import compose.icons.fontawesomeicons.solid.Search
import compose.icons.fontawesomeicons.solid.Star
import compose.icons.fontawesomeicons.solid.Th
import compose.icons.fontawesomeicons.solid.User
import core.data.calendar.CalendarPermissionDecision
import core.data.calendar.ICalendarEventSource
import core.data.calendar.ICalendarPermissionGate
import core.data.homerole.IHomeRoleSource
import core.data.notifications.INotificationSource
import core.data.prefs.ContactResultsOutcome
import core.data.prefs.IPreferencesRepository
import core.data.prefs.ISearchSettings
import core.data.search.IContactSearchSource
import core.ui.R as CoreUiR
import core.ui.composables.OnResume
import core.ui.composables.PermissionDeniedDialog
import core.ui.composables.rememberPermissionResultLauncher
import core.ui.homerole.rememberHomeRoleRequest
import core.ui.model.data.Destination
import core.ui.model.data.LAUNCHER_ROUTE
import core.ui.model.data.ONBOARDING_ROUTE
import core.ui.showDestination
import core.ui.widgets.openRailNotificationListenerSettings
import feature.launcher.R
import org.koin.compose.koinInject

private const val FIRST_ONBOARDING_PAGE_INDEX: Int = 0
private val onboardingContentMaxWidth: Dp = 480.dp
private val onboardingContentPadding: Dp = 24.dp
private val onboardingSectionSpacing: Dp = 24.dp
private val onboardingIconSize: Dp = 56.dp
private val onboardingIconPadding: Dp = 24.dp
private val onboardingControlsElevation: Dp = 8.dp

/** The first-run flow, also opened explicitly by Replay onboarding in Rail Settings. */
val onboardingDestination: Destination = Destination(
    route = ONBOARDING_ROUTE,
    content = { navController: NavController, _: NavBackStackEntry ->
        OnboardingScreen(navController = navController)
    }
)

internal enum class OnboardingPermission(
    @StringRes val requestLabel: Int,
    @StringRes val requestContentDescription: Int,
    @StringRes val deniedTitle: Int,
    @StringRes val deniedMessage: Int
) {
    NotificationAccess(
        CoreUiR.string.open_settings,
        R.string.onboarding_notification_access_request,
        CoreUiR.string.allow_notification_access_title,
        CoreUiR.string.allow_notification_access_body
    ),
    Calendar(
        CoreUiR.string.grant,
        R.string.onboarding_calendar_request,
        CoreUiR.string.calendar_permission_denied_title,
        CoreUiR.string.calendar_permission_denied_message
    ),
    Contacts(
        CoreUiR.string.grant,
        R.string.onboarding_contacts_request,
        CoreUiR.string.search_contacts_permission_title,
        CoreUiR.string.search_contacts_permission_summary
    )
}

internal data class OnboardingPermissionState(
    val hasNotificationAccess: Boolean = false,
    val hasCalendarAccess: Boolean = false,
    val hasContactsAccess: Boolean = false
)

internal enum class OnboardingPage(
    @StringRes val title: Int,
    @StringRes val body: Int,
    val icon: ImageVector,
    val permission: OnboardingPermission? = null
) {
    Rail(R.string.onboarding_rail_title, R.string.onboarding_rail_body, FontAwesomeIcons.Solid.Th),
    Favorites(R.string.onboarding_favorites_title, R.string.onboarding_favorites_body, FontAwesomeIcons.Solid.Star),
    Search(R.string.onboarding_search_title, R.string.onboarding_search_body, FontAwesomeIcons.Solid.Search),
    PopUps(R.string.onboarding_pop_ups_title, R.string.onboarding_pop_ups_body, FontAwesomeIcons.Solid.HandPointer),
    Widgets(R.string.onboarding_widgets_title, R.string.onboarding_widgets_body, FontAwesomeIcons.Solid.Clock),
    NotificationAccess(
        R.string.onboarding_notification_access_title,
        CoreUiR.string.allow_notification_access_body,
        FontAwesomeIcons.Solid.Bell,
        OnboardingPermission.NotificationAccess
    ),
    Calendar(
        CoreUiR.string.upcoming_events,
        R.string.onboarding_calendar_body,
        FontAwesomeIcons.Solid.Calendar,
        OnboardingPermission.Calendar
    ),
    Contacts(
        CoreUiR.string.search_contacts,
        R.string.onboarding_contacts_body,
        FontAwesomeIcons.Solid.User,
        OnboardingPermission.Contacts
    ),
    HomeRole(CoreUiR.string.set_rail_as_home, R.string.onboarding_home_role_body, FontAwesomeIcons.Solid.Check)
}

/** Shows onboarding pages, persists completion and returns Home. */
@Composable
fun OnboardingScreen(
    navController: NavController,
    preferencesRepository: IPreferencesRepository = koinInject(),
    homeRoleSource: IHomeRoleSource = koinInject(),
    calendarEventSource: ICalendarEventSource = koinInject(),
    calendarPermissionGate: ICalendarPermissionGate = koinInject(),
    contactSearchSource: IContactSearchSource = koinInject(),
    searchSettings: ISearchSettings = koinInject(),
    notificationSource: INotificationSource = koinInject()
) {
    val context: Context = LocalContext.current
    var deniedPermission: OnboardingPermission? by remember { mutableStateOf(null) }
    val hasNotificationAccess: Boolean by notificationSource.hasAccess.collectAsState()
    val hasCalendarAccess: Boolean by calendarEventSource.hasPermission.collectAsState()
    val hasContactsAccess: Boolean by contactSearchSource.hasPermission.collectAsState()
    val calendarPermissionLauncher: ManagedActivityResultLauncher<String, Boolean> =
        rememberPermissionResultLauncher(permission = Manifest.permission.READ_CALENDAR) { granted, canAskAgain ->
            val decision: CalendarPermissionDecision = calendarPermissionGate.onPermissionResult(
                granted = granted,
                canAskAgain = canAskAgain
            )
            deniedPermission = if (decision is CalendarPermissionDecision.Denied && decision.permanentlyDenied) {
                OnboardingPermission.Calendar
            } else {
                null
            }
        }
    val contactsPermissionLauncher: ManagedActivityResultLauncher<String, Boolean> =
        rememberPermissionResultLauncher(permission = Manifest.permission.READ_CONTACTS) { granted, canAskAgain ->
            val outcome: ContactResultsOutcome = searchSettings.onContactsPermissionResult(
                granted = granted,
                canAskAgain = canAskAgain
            )
            deniedPermission = if (outcome == ContactResultsOutcome.ContactsPermissionDenied) {
                OnboardingPermission.Contacts
            } else {
                null
            }
        }
    val refreshPermissions: () -> Unit = {
        calendarPermissionGate.refreshPermission()
        searchSettings.refreshContactsPermission()
        notificationSource.refreshAccess()
    }
    val onRequestPermission: (OnboardingPermission) -> Unit = { inputPermission: OnboardingPermission ->
        when (inputPermission) {
            OnboardingPermission.NotificationAccess -> context.openRailNotificationListenerSettings()
            OnboardingPermission.Calendar -> calendarPermissionLauncher.launch(Manifest.permission.READ_CALENDAR)
            OnboardingPermission.Contacts -> contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
    }
    val onRequestHomeRole: () -> Unit = rememberHomeRoleRequest(homeRoleSource)
    val onComplete: () -> Unit = remember(navController, preferencesRepository) {
        {
            preferencesRepository.setOnboardingCompleted(true)
            showDestination(navController = navController, route = LAUNCHER_ROUTE, popUpToRoute = ONBOARDING_ROUTE)
        }
    }
    LaunchedEffect(context, calendarPermissionGate, searchSettings, notificationSource) {
        refreshPermissions()
    }
    OnResume(refreshPermissions)
    OnboardingContent(
        onComplete = onComplete,
        onRequestHomeRole = onRequestHomeRole,
        onRequestPermission = onRequestPermission,
        permissionState = OnboardingPermissionState(
            hasNotificationAccess = hasNotificationAccess,
            hasCalendarAccess = hasCalendarAccess,
            hasContactsAccess = hasContactsAccess
        )
    )
    deniedPermission?.let { inputPermission: OnboardingPermission ->
        PermissionDeniedDialog(
            title = stringResource(inputPermission.deniedTitle),
            message = stringResource(inputPermission.deniedMessage),
            onDismiss = { deniedPermission = null }
        )
    }
}

private fun OnboardingPermissionState.isGranted(inputPermission: OnboardingPermission): Boolean = when (inputPermission) {
    OnboardingPermission.NotificationAccess -> hasNotificationAccess
    OnboardingPermission.Calendar -> hasCalendarAccess
    OnboardingPermission.Contacts -> hasContactsAccess
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OnboardingContent(
    onComplete: () -> Unit,
    onRequestHomeRole: () -> Unit,
    onRequestPermission: (OnboardingPermission) -> Unit,
    permissionState: OnboardingPermissionState
) {
    var pageIndex: Int by rememberSaveable { mutableIntStateOf(FIRST_ONBOARDING_PAGE_INDEX) }
    val currentPage: OnboardingPage = OnboardingPage.entries[pageIndex]
    val isLastPage: Boolean = pageIndex == OnboardingPage.entries.lastIndex
    val permissionRequest: OnboardingPermission? = currentPage.permission?.takeUnless { permissionState.isGranted(it) }
    val mainActionLabel: Int = when {
        isLastPage -> R.string.onboarding_finish
        permissionRequest != null -> permissionRequest.requestLabel
        else -> CoreUiR.string.next
    }
    val mainActionContentDescription: String? = if (permissionRequest != null) {
        stringResource(permissionRequest.requestContentDescription)
    } else {
        null
    }
    val progressDescription: String = stringResource(
        R.string.onboarding_step,
        pageIndex + 1,
        OnboardingPage.entries.size
    )
    val onMainAction: () -> Unit = {
        if (isLastPage) {
            onRequestHomeRole()
            onComplete()
        } else if (permissionRequest != null) {
            onRequestPermission(permissionRequest)
        } else {
            pageIndex++
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.onboarding_welcome)) },
                actions = {
                    TextButton(onClick = onComplete) {
                        Text(stringResource(R.string.onboarding_skip))
                    }
                }
            )
        },
        bottomBar = {
            Surface(shadowElevation = onboardingControlsElevation) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = onboardingContentPadding, vertical = onboardingSectionSpacing),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (pageIndex > FIRST_ONBOARDING_PAGE_INDEX) {
                        TextButton(onClick = { pageIndex-- }) {
                            Text(stringResource(CoreUiR.string.back))
                        }
                    }
                    if (pageIndex > FIRST_ONBOARDING_PAGE_INDEX || permissionRequest != null) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                    if (permissionRequest != null) {
                        TextButton(onClick = { pageIndex++ }) {
                            Text(stringResource(CoreUiR.string.not_now))
                        }
                    }
                    Button(
                        onClick = onMainAction,
                        modifier = Modifier.semantics {
                            mainActionContentDescription?.let { contentDescription = it }
                        }
                    ) {
                        Text(stringResource(mainActionLabel))
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = onboardingContentMaxWidth)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(onboardingContentPadding),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                LinearProgressIndicator(
                    progress = { (pageIndex + 1).toFloat() / OnboardingPage.entries.size },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = onboardingSectionSpacing)
                        .clearAndSetSemantics { contentDescription = progressDescription }
                )
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(
                        imageVector = currentPage.icon,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(onboardingIconPadding)
                            .size(onboardingIconSize)
                    )
                }
                Spacer(modifier = Modifier.height(onboardingSectionSpacing))
                Text(
                    text = stringResource(currentPage.title),
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stringResource(currentPage.body),
                    modifier = Modifier.padding(top = onboardingSectionSpacing),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                if (currentPage.permission != null && permissionRequest == null) {
                    Text(
                        text = stringResource(CoreUiR.string.permission_granted),
                        modifier = Modifier.padding(top = onboardingSectionSpacing),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(modifier = Modifier.height(onboardingContentPadding))
            }
        }
    }
}
