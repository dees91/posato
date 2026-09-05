package app.posato.prototype.model

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf

object PrototypeClock {
    const val NOW: String = "17:45"
    const val DEFAULT_MINUTES: Int = 25
    const val EXAMPLE_MINUTES: Int = 45
    const val MINIMUM_MINUTES: Int = 5
    const val MAXIMUM_MINUTES: Int = 1_440
    private const val MINUTES_PER_HOUR: Int = 60
    private const val DEMO_HOUR: Int = 17
    private const val DEMO_MINUTE: Int = 45
    private const val HOURS_PER_DAY: Int = 24

    fun resolvedEnd(minutes: Int): String {
        val total = DEMO_HOUR * MINUTES_PER_HOUR + DEMO_MINUTE + minutes
        val hours = (total / MINUTES_PER_HOUR % HOURS_PER_DAY).toString().padStart(2, '0')
        val remainder = (total % MINUTES_PER_HOUR).toString().padStart(2, '0')
        val suffix = if (total >= MAXIMUM_MINUTES) " tomorrow" else ""

        return "$hours:$remainder$suffix"
    }
}

object PrototypeFixtures {
    const val DOMAIN: String = "news.example"
    const val APPLICATION: String = "Example Social"
    const val APPLICATION_GROUP: String = "Social feeds"

    fun applications(platform: PrototypePlatform): PersistentList<String> {
        return when (platform) {
            PrototypePlatform.Mac -> persistentListOf(APPLICATION, "Community Desk", "Loop Video", "Chat Stream")
            PrototypePlatform.IPhone -> persistentListOf(APPLICATION, "Pocket Community", "Short Video", "Chat Stream")
        }
    }

    fun ready(platform: PrototypePlatform): PrototypeState {
        return PrototypeState(
            platform = platform,
            surface = PrototypeSurface.Home,
            onboardingComplete = true,
            workspace = PrototypeWorkspace(WorkspaceStatus.Connected, WorkspaceKey.Ready, remotePolicyAvailable = true),
            permission = PrototypePermission.Granted,
            policy = PrototypePolicy(
                domains = persistentListOf(DOMAIN),
                applicationGroup = APPLICATION_GROUP,
                mappings = persistentMapOf(
                    PrototypePlatform.Mac to persistentListOf(APPLICATION),
                    PrototypePlatform.IPhone to persistentListOf(APPLICATION),
                ),
            ),
            sync = PrototypeSync(PrototypeSyncStatus.Completed, lastCompletedOnDevice = "17:42"),
        ).withOutcome("Setup is complete on this device. No session is active.", OutcomeTone.Success)
    }

    fun active(platform: PrototypePlatform): PrototypeState {
        return ready(platform).copy(
            surface = PrototypeSurface.Active,
            session = PrototypeSession(true, PrototypeClock.EXAMPLE_MINUTES, PrototypeClock.resolvedEnd(PrototypeClock.EXAMPLE_MINUTES)),
        )
    }

    fun recovery(platform: PrototypePlatform): PrototypeState {
        return ready(platform)
            .withLocalApplications(persistentListOf())
            .copy(
                permission = PrototypePermission.Revoked,
                sync = PrototypeSync(PrototypeSyncStatus.ActionRequired, lastCompletedOnDevice = "17:42"),
            )
    }

    fun waiting(platform: PrototypePlatform): PrototypeState {
        return reducePrototype(
            reducePrototype(
                reducePrototype(PrototypeState(platform), SetupAction.ShowPrivacy),
                SetupAction.SyncWithICloud,
            ),
            SetupAction.DiscoverDelayedKey,
        )
    }
}
