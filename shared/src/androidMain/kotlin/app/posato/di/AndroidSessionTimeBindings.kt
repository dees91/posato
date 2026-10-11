package app.posato.di

import app.posato.PosatoApplication
import app.posato.core.database.PosatoDatabase
import app.posato.feature.enforcement.EnforcementOutcome
import app.posato.feature.enforcement.EnforcementPort
import app.posato.feature.enforcement.PauseClaims
import app.posato.feature.enforcement.PauseLimits
import app.posato.feature.notifications.SessionNotificationPlatform
import app.posato.feature.notifications.SessionNotifier
import app.posato.feature.onboarding.ApplicationAccessPort
import app.posato.feature.onboarding.OnboardingDependencies
import app.posato.feature.onboarding.OnboardingPermissionPlatform
import app.posato.feature.onboarding.UnavailableMacHelper
import app.posato.feature.onboarding.data.SqlLocalSetupStore
import app.posato.feature.schedules.JavaScheduleZone
import app.posato.feature.schedules.ScheduleDependencies
import app.posato.feature.schedules.data.ScheduleSyncStore
import app.posato.feature.schedules.domain.ScheduleZone
import app.posato.feature.schedules.host.ScheduleHost
import app.posato.feature.schedules.host.ScheduleHostPorts
import app.posato.feature.schedules.host.ScheduleStartGate
import app.posato.feature.schedules.host.ScheduledPauses
import app.posato.feature.schedules.host.StartGate
import app.posato.feature.session.JvmSessionTimeFormat
import app.posato.feature.session.data.LocalSessionSyncStore
import app.posato.feature.session.data.SqlLocalSessionStore
import app.posato.feature.session.data.SqlPartRetentionStore
import app.posato.feature.session.domain.RandomSessionIdGenerator
import app.posato.feature.session.domain.SessionClock
import app.posato.feature.session.domain.SessionIdGenerator
import app.posato.feature.session.domain.SessionTimeFormat
import app.posato.feature.session.ui.SessionComposition
import app.posato.feature.session.ui.SessionTransitionOwner
import app.posato.feature.session.ui.loadSessionTargets
import app.posato.feature.session.ui.recompose
import app.posato.feature.sync.bootstrap.AppleSync
import app.posato.feature.sync.bootstrap.BootstrapCoordinator
import app.posato.feature.sync.bootstrap.ScheduleSync
import app.posato.feature.sync.bootstrap.SqlBootstrapStore
import app.posato.feature.sync.data.JdkSyncCryptoProvider
import app.posato.feature.sync.data.SqlSyncReplicaStore
import app.posato.feature.sync.domain.SyncOperationCore
import app.posato.feature.sync.domain.SyncWallClock
import app.posato.feature.sync.folder.FolderSync
import app.posato.feature.sync.folder.FolderSyncControls
import app.posato.feature.sync.folder.KeystoreKeyItemProtection
import app.posato.feature.targets.data.LocalApplicationMappings
import app.posato.feature.targets.data.LocalPolicySyncStore
import app.posato.feature.targets.data.LocalTargetPolicyStore
import app.posato.feature.targets.data.PauseSetPreparation
import app.posato.feature.targets.data.SqlLocalTargetPolicyStore
import app.posato.feature.targets.data.SyncTargetPolicyStore
import app.posato.feature.targets.data.retainSets
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.createGraphFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

/** The session clock, identifiers, and time format on Android. */
internal interface AndroidSessionTimeBindings {
    @Provides
    @SingleIn(AppScope::class)
    fun provideSessionIds(): SessionIdGenerator {
        return RandomSessionIdGenerator
    }

    @Provides
    @SingleIn(AppScope::class)
    fun provideSessionClock(): SessionClock {
        return SessionClock { System.currentTimeMillis() }
    }

    @Provides
    @SingleIn(AppScope::class)
    fun provideSessionTimeFormat(): SessionTimeFormat {
        return JvmSessionTimeFormat()
    }
}
