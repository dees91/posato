package app.posato.provisioning.store

import app.posato.provisioning.core.ErrorCode
import app.posato.provisioning.core.ProvisioningException

private const val MAX_SUBTITLE = 30

/** The App Store subtitle input check, made before any request; [StoreAppInfo] writes it. */
object StoreSubtitle {
    fun input(value: String): String {
        val text = value.trim()
        val problem = when {
            text.isEmpty() -> "--subtitle is empty."
            text.length > MAX_SUBTITLE -> "--subtitle is longer than App Store Connect's $MAX_SUBTITLE characters."
            else -> null
        }
        if (problem != null) {
            throw ProvisioningException(ErrorCode.RELEASE_INPUT_INVALID, problem, "Correct the input; nothing was sent to App Store Connect.")
        }
        return text
    }
}
