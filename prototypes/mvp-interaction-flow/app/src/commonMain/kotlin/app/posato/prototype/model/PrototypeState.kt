package app.posato.prototype.model

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf

enum class PrototypePlatform(
    val label: String
) {
    Mac("Mac"),
    IPhone("iPhone")
}

enum class PrototypeSurface {
    Welcome,
    Privacy,
    WorkspaceCheck,
    KeyWait,
    Permission,
    Targets,
    DomainEditor,
    AppPicker,
    Items,
    Home,
    SessionSetup,
    SessionReview,
    Active,
    Blocked,
    EarlyEnd,
    Recovery
}

enum class WorkspaceStatus { NotConnected, Checking, Found, Connected }

enum class WorkspaceKey { NotRequested, Waiting, Ready }

enum class PrototypePermission { NotRequested, Granted, Revoked }

enum class PrototypeSyncStatus { LocalOnly, Pending, Syncing, Completed, Retryable, WaitingForKey, ActionRequired }

enum class OutcomeTone { Info, Success, Warning, Blocked }

data class PrototypeWorkspace(
    val status: WorkspaceStatus = WorkspaceStatus.NotConnected,
    val key: WorkspaceKey = WorkspaceKey.NotRequested,
    val remotePolicyAvailable: Boolean = false
)

data class PrototypePolicy(
    val domains: PersistentList<String> = persistentListOf(),
    val applicationGroup: String? = null,
    val mappings: PersistentMap<PrototypePlatform, PersistentList<String>> = persistentMapOf()
)

data class PrototypeEditor(
    val returnSurface: PrototypeSurface = PrototypeSurface.Targets,
    val originalDomain: String? = null,
    val domainError: String? = null,
    val applicationError: String? = null,
    val sessionKey: Int = 0
)

data class PrototypeSession(
    val active: Boolean = false,
    val durationMinutes: Int = PrototypeClock.DEFAULT_MINUTES,
    val endsAt: String? = null
)

data class PrototypeSync(
    val status: PrototypeSyncStatus = PrototypeSyncStatus.LocalOnly,
    val pendingWork: Boolean = false,
    val lastCompletedOnDevice: String? = null
)

data class PrototypeOutcome(
    val message: String,
    val tone: OutcomeTone = OutcomeTone.Info
)

data class PrototypeState(
    val platform: PrototypePlatform,
    val surface: PrototypeSurface = PrototypeSurface.Welcome,
    val onboardingComplete: Boolean = false,
    val workspace: PrototypeWorkspace = PrototypeWorkspace(),
    val permission: PrototypePermission = PrototypePermission.NotRequested,
    val policy: PrototypePolicy = PrototypePolicy(),
    val editor: PrototypeEditor = PrototypeEditor(),
    val websiteEntry: PrototypeWebsiteEntry = PrototypeWebsiteEntry(),
    val session: PrototypeSession = PrototypeSession(),
    val sync: PrototypeSync = PrototypeSync(),
    val outcome: PrototypeOutcome = PrototypeOutcome("Prototype ready. Choose a walkthrough or use Free play.")
) {
    fun localApplications(): PersistentList<String> {
        return policy.mappings[platform] ?: persistentListOf()
    }

    fun effectiveItemCount(): Int {
        return policy.domains.size + localApplications().size
    }

    fun reviewIssues(): PersistentList<String> {
        var issues = persistentListOf<String>()
        if (permission != PrototypePermission.Granted) issues = issues.adding("Local permission needs your attention.")
        if (policy.applicationGroup != null && localApplications().isEmpty()) {
            issues = issues.adding("Choose applications on this ${platform.label}.")
        }
        if (effectiveItemCount() == 0) issues = issues.adding("Choose at least one item to pause on this device.")

        return issues
    }

    fun withOutcome(
        message: String,
        tone: OutcomeTone = OutcomeTone.Info
    ): PrototypeState {
        return copy(outcome = PrototypeOutcome(message, tone))
    }

    fun blocked(message: String): PrototypeState {
        return withOutcome("Nothing changed. $message", OutcomeTone.Blocked)
    }

    fun pending(message: String): PrototypeState {
        return copy(sync = sync.copy(status = PrototypeSyncStatus.Pending, pendingWork = true)).withOutcome(message)
    }

    fun withLocalApplications(applications: PersistentList<String>): PrototypeState {
        return copy(policy = policy.copy(mappings = policy.mappings.putting(platform, applications)))
    }
}
