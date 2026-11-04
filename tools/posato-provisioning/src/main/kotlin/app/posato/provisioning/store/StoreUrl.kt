package app.posato.provisioning.store

import app.posato.provisioning.core.ErrorCode
import app.posato.provisioning.core.ProvisioningException
import java.net.URI

/** The input check for a store link, such as the support or privacy policy URL, made before any request. */
object StoreUrl {
    /** [value] when it is an absolute HTTPS URL with a host and no spaces; [option] names the input in the failure. */
    fun input(
        value: String,
        option: String,
    ): String {
        val host = runCatching { URI(value) }.getOrNull()?.takeIf { uri -> uri.scheme == "https" }?.host
        if (host.isNullOrEmpty()) {
            throw ProvisioningException(
                ErrorCode.RELEASE_INPUT_INVALID,
                "$option must be an absolute HTTPS URL such as https://posato.app/support/.",
                "Correct the input; nothing was sent to App Store Connect.",
            )
        }
        return value
    }
}
