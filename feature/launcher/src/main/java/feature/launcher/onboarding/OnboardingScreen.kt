package feature.launcher.onboarding

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
import compose.icons.fontawesomeicons.solid.Check
import compose.icons.fontawesomeicons.solid.Clock
import compose.icons.fontawesomeicons.solid.HandPointer
import compose.icons.fontawesomeicons.solid.Search
import compose.icons.fontawesomeicons.solid.Star
import compose.icons.fontawesomeicons.solid.Th
import core.data.homerole.IHomeRoleSource
import core.data.prefs.IPreferencesRepository
import core.ui.R as CoreUiR
import core.ui.homerole.rememberHomeRoleRequest
import core.ui.model.data.Destination
import core.ui.model.data.LAUNCHER_ROUTE
import core.ui.model.data.ONBOARDING_ROUTE
import core.ui.showDestination
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

internal enum class OnboardingPage(@StringRes val title: Int, @StringRes val body: Int, val icon: ImageVector) {
    Rail(R.string.onboarding_rail_title, R.string.onboarding_rail_body, FontAwesomeIcons.Solid.Th),
    Favorites(R.string.onboarding_favorites_title, R.string.onboarding_favorites_body, FontAwesomeIcons.Solid.Star),
    Search(R.string.onboarding_search_title, R.string.onboarding_search_body, FontAwesomeIcons.Solid.Search),
    PopUps(R.string.onboarding_pop_ups_title, R.string.onboarding_pop_ups_body, FontAwesomeIcons.Solid.HandPointer),
    Widgets(R.string.onboarding_widgets_title, R.string.onboarding_widgets_body, FontAwesomeIcons.Solid.Clock),
    HomeRole(CoreUiR.string.set_rail_as_home, R.string.onboarding_home_role_body, FontAwesomeIcons.Solid.Check)
}

/** Shows static onboarding pages, persists completion and returns Home. */
@Composable
fun OnboardingScreen(
    navController: NavController,
    preferencesRepository: IPreferencesRepository = koinInject(),
    homeRoleSource: IHomeRoleSource = koinInject()
) {
    val onRequestHomeRole: () -> Unit = rememberHomeRoleRequest(homeRoleSource)
    val onComplete: () -> Unit = remember(navController, preferencesRepository) {
        {
            preferencesRepository.setOnboardingCompleted(true)
            showDestination(navController = navController, route = LAUNCHER_ROUTE, popUpToRoute = ONBOARDING_ROUTE)
        }
    }
    OnboardingContent(
        onComplete = onComplete,
        onRequestHomeRole = onRequestHomeRole
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OnboardingContent(onComplete: () -> Unit, onRequestHomeRole: () -> Unit) {
    var pageIndex: Int by rememberSaveable { mutableIntStateOf(FIRST_ONBOARDING_PAGE_INDEX) }
    val currentPage: OnboardingPage = OnboardingPage.entries[pageIndex]
    val isLastPage: Boolean = pageIndex == OnboardingPage.entries.lastIndex
    val progressDescription: String = stringResource(
        R.string.onboarding_step,
        pageIndex + 1,
        OnboardingPage.entries.size
    )
    val onNext: () -> Unit = {
        if (isLastPage) {
            onRequestHomeRole()
            onComplete()
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
                        Spacer(modifier = Modifier.weight(1f))
                    }
                    Button(onClick = onNext) {
                        Text(
                            stringResource(
                                if (isLastPage) R.string.onboarding_finish else CoreUiR.string.next
                            )
                        )
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
                Spacer(modifier = Modifier.height(onboardingContentPadding))
            }
        }
    }
}
