package core.data.testing

import android.content.ComponentName
import android.content.IntentSender
import android.graphics.drawable.Drawable
import android.os.UserHandle
import core.data.profiles.IProfileSource
import core.data.profiles.ProfileActivity
import core.data.profiles.ProfileChange
import core.data.profiles.ProfileInfo
import core.data.profiles.ProfileKind
import core.data.profiles.QuietRequestResult
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

/** The mutable state behind a [createProfileSourceMock] source. */
class ProfileSourceState(profiles: List<ProfileInfo> = emptyList()) {
    val profiles: MutableStateFlow<List<ProfileInfo>> = MutableStateFlow(profiles)
    val changes: MutableSharedFlow<ProfileChange> = MutableSharedFlow(extraBufferCapacity = 32)
    val activitiesByUser: MutableMap<UserHandle, List<ProfileActivity>> = mutableMapOf()
    val usersBySerial: MutableMap<Long, UserHandle> = profiles.associate { profile -> profile.serial to profile.user }.toMutableMap()
    val serialsByUser: MutableMap<UserHandle, Long> = profiles.associate { profile -> profile.user to profile.serial }.toMutableMap()

    /** The system-badged label for a label and Profile; identity unless a test overrides it. */
    var badgedLabel: (String, UserHandle) -> String = { label, _ -> label }

    /** The unbadged activity icon for a component and Profile; null unless a test overrides it. */
    var activityIcon: (ComponentName, UserHandle) -> Drawable? = { _, _ -> null }

    /** The unbadged application icon for a package and Profile; null unless a test overrides it. */
    var applicationIcon: (String, UserHandle) -> Drawable? = { _, _ -> null }

    /** The outcome of [IProfileSource.requestQuiet]; [QuietRequestResult.Changed] unless a test overrides it. */
    var quietRequestResult: QuietRequestResult = QuietRequestResult.Changed

    /** The system Private Space settings screen; null unless a test provides one. */
    var privateSpaceSettingsIntent: IntentSender? = null
}

/** A MockK [IProfileSource] whose only listed Profile is [profile]. */
fun createProfileSourceMock(profile: ProfileInfo): IProfileSource =
    createProfileSourceMock(state = ProfileSourceState(profiles = listOf(profile)))

/** A MockK [IProfileSource] reading [state]. */
fun createProfileSourceMock(state: ProfileSourceState = ProfileSourceState()): IProfileSource {
    val mockProfileSource: IProfileSource = mockk()
    every { mockProfileSource.profiles } returns state.profiles
    every { mockProfileSource.changes } returns state.changes
    coEvery { mockProfileSource.refreshProfiles() } just Runs
    every { mockProfileSource.profileFor(any()) } answers {
        state.profiles.value.firstOrNull { profile -> profile.user == firstArg<UserHandle>() }
    }
    every { mockProfileSource.kindFor(any()) } answers {
        state.profiles.value.firstOrNull { profile -> profile.user == firstArg<UserHandle>() }?.kind ?: ProfileKind.Other
    }
    every { mockProfileSource.badgedLabel(any(), any()) } answers {
        state.badgedLabel(firstArg<String>(), secondArg<UserHandle>())
    }
    every { mockProfileSource.activities(any<UserHandle>()) } answers {
        state.activitiesByUser[firstArg<UserHandle>()].orEmpty()
    }
    every { mockProfileSource.activities(any<String>(), any<UserHandle>()) } answers {
        state.activitiesByUser[secondArg<UserHandle>()].orEmpty()
            .filter { activity -> activity.componentName.packageName == firstArg<String>() }
    }
    every { mockProfileSource.activityIcon(any(), any()) } answers {
        state.activityIcon(firstArg<ComponentName>(), secondArg<UserHandle>())
    }
    every { mockProfileSource.applicationIcon(any(), any()) } answers {
        state.applicationIcon(firstArg<String>(), secondArg<UserHandle>())
    }
    coEvery { mockProfileSource.requestQuiet(any(), any()) } answers { state.quietRequestResult }
    every { mockProfileSource.userForSerial(any()) } answers { state.usersBySerial[firstArg<Long>()] }
    every { mockProfileSource.serialForUser(any()) } answers { state.serialsByUser[firstArg<UserHandle>()] }
    coEvery { mockProfileSource.privateSpaceSettingsIntent() } answers { state.privateSpaceSettingsIntent }
    return mockProfileSource
}
