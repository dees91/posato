package app.posato.feature.enforcement

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

public interface BrowserEnforcementLink {
    public fun start(
        domains: List<String>,
        sessionEndEpochMillis: Long,
    ): Boolean

    /**
     * Reports [EnforcementOutcome.CLEARED] when nothing remains enforced,
     * [EnforcementOutcome.UNAVAILABLE] when the helper service is not registered,
     * approved, or compatible (nothing can be owned there), and
     * [EnforcementOutcome.FAILED] otherwise. May throw when the helper cannot be reached.
     */
    public fun clear(): EnforcementOutcome

    public fun isApplied(): Boolean?
}

public interface ApplicationEnforcementLink {
    public suspend fun start(
        mappingIds: List<String>,
        sessionEndEpochMillis: Long,
    ): Boolean

    /**
     * Reports [EnforcementOutcome.CLEARED] when nothing remains enforced,
     * [EnforcementOutcome.UNAVAILABLE] when the helper service is not registered,
     * approved, or compatible (nothing can be owned there), and
     * [EnforcementOutcome.FAILED] otherwise. May throw when the helper cannot be reached.
     */
    public suspend fun clear(): EnforcementOutcome

    public suspend fun isServiceReady(): Boolean?
}

public class JvmSessionEnforcement(
    private val browser: BrowserEnforcementLink,
    private val applications: ApplicationEnforcementLink,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : EnforcementPort {
    override val reapplyRequiresPrompt: Boolean = true

    private var appliedBrowsers: Boolean = false
    private var appliedApplications: Boolean = false

    override suspend fun apply(request: EnforcementRequest): EnforcementApplyReport {
        return withContext(ioDispatcher) {
            if (request.domains.isEmpty() && request.mappingIds.isEmpty()) {
                EnforcementApplyReport(EnforcementOutcome.NOTHING_TO_ENFORCE, false, false)
            } else {
                applyBoth(request)
            }
        }
    }

    override suspend fun clear(): EnforcementOutcome {
        return withContext(ioDispatcher) {
            try {
                val browserOutcome = browser.clear()
                val applicationsOutcome = applications.clear()
                appliedBrowsers = false
                appliedApplications = false
                combineClearOutcomes(browserOutcome, applicationsOutcome)
            } catch (_: Exception) {
                val owned = appliedBrowsers || appliedApplications
                appliedBrowsers = false
                appliedApplications = false
                if (owned) {
                    EnforcementOutcome.FAILED
                } else {
                    EnforcementOutcome.UNAVAILABLE
                }
            }
        }
    }

    override suspend fun status(): EnforcementOutcome {
        return withContext(ioDispatcher) {
            if (!appliedBrowsers && !appliedApplications) {
                EnforcementOutcome.CLEARED
            } else if (appliedBrowsers) {
                when (browser.isApplied()) {
                    true -> EnforcementOutcome.APPLIED
                    false -> EnforcementOutcome.CLEARED
                    null -> EnforcementOutcome.UNKNOWN
                }
            } else {
                when (applications.isServiceReady()) {
                    true -> EnforcementOutcome.APPLIED
                    false -> EnforcementOutcome.CLEARED
                    null -> EnforcementOutcome.UNKNOWN
                }
            }
        }
    }

    private suspend fun applyBoth(request: EnforcementRequest): EnforcementApplyReport {
        if (request.domains.isNotEmpty()) {
            if (!browser.start(request.domains, request.sessionEndEpochMillis)) {
                appliedBrowsers = false
                appliedApplications = false
                return EnforcementApplyReport(EnforcementOutcome.FAILED, false, true)
            }
            appliedBrowsers = true
        } else {
            appliedBrowsers = false
        }
        if (request.mappingIds.isNotEmpty()) {
            if (!applications.start(request.mappingIds, request.sessionEndEpochMillis)) {
                browser.clear()
                appliedBrowsers = false
                appliedApplications = false
                return EnforcementApplyReport(EnforcementOutcome.FAILED, false, true)
            }
            appliedApplications = true
        } else {
            appliedApplications = false
        }
        return EnforcementApplyReport(EnforcementOutcome.APPLIED, false, false)
    }

    private fun combineClearOutcomes(
        browserOutcome: EnforcementOutcome,
        applicationsOutcome: EnforcementOutcome,
    ): EnforcementOutcome {
        return if (browserOutcome == EnforcementOutcome.CLEARED && applicationsOutcome == EnforcementOutcome.CLEARED) {
            EnforcementOutcome.CLEARED
        } else if (browserOutcome == EnforcementOutcome.FAILED || applicationsOutcome == EnforcementOutcome.FAILED) {
            EnforcementOutcome.FAILED
        } else {
            EnforcementOutcome.UNAVAILABLE
        }
    }
}
