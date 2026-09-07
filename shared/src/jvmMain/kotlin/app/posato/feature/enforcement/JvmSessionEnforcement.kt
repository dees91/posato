package app.posato.feature.enforcement

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

public interface BrowserEnforcementLink {
    public fun start(
        domains: List<String>,
        sessionEndEpochMillis: Long,
    ): Boolean

    public fun clear(): Boolean

    public fun isApplied(): Boolean?
}

public interface ApplicationEnforcementLink {
    public suspend fun start(
        mappingIds: List<String>,
        sessionEndEpochMillis: Long,
    ): Boolean

    public suspend fun clear(): Boolean
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
            val browserCleared = browser.clear()
            val applicationsCleared = applications.clear()
            appliedBrowsers = false
            appliedApplications = false
            if (browserCleared && applicationsCleared) {
                EnforcementOutcome.CLEARED
            } else {
                EnforcementOutcome.FAILED
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
                EnforcementOutcome.APPLIED
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
}
