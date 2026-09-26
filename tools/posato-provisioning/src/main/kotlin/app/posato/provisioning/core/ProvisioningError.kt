package app.posato.provisioning.core

private const val EXIT_FAILED = 1
private const val EXIT_USAGE = 2
private const val EXIT_PRECONDITION = 3
private const val EXIT_NOT_FOUND = 4

/**
 * Why a command stopped, and what that costs the caller.
 *
 * The exit codes mirror the verification driver so an agent can branch on them the same way: 2 is a usage mistake,
 * 3 is a condition the maintainer must clear before retrying, 4 is a resource the account does not have, and 1 is
 * everything that failed while doing the work.
 */
enum class ErrorCode(
    val exitCode: Int
) {
    USAGE(EXIT_USAGE),
    CONFIGURATION_MISSING(EXIT_PRECONDITION),
    ASC_KEY_UNREADABLE(EXIT_PRECONDITION),
    ASC_TOKEN_FAILED(EXIT_PRECONDITION),
    ASC_UNAUTHORIZED(EXIT_PRECONDITION),
    ASC_FORBIDDEN(EXIT_PRECONDITION),
    ASC_NOT_FOUND(EXIT_NOT_FOUND),
    ASC_CONFLICT(EXIT_FAILED),
    ASC_REJECTED(EXIT_FAILED),
    ASC_RATE_LIMITED(EXIT_FAILED),
    ASC_UNAVAILABLE(EXIT_FAILED),
    ASC_UNREACHABLE(EXIT_FAILED),
    ASC_RESPONSE_TOO_LARGE(EXIT_FAILED),
    ASC_TOO_MANY_RESULTS(EXIT_FAILED),
    BUNDLE_ID_MISSING(EXIT_NOT_FOUND),
    CERTIFICATE_MISSING(EXIT_PRECONDITION),
    CERTIFICATE_IMPORT_FAILED(EXIT_FAILED),
    DEVICE_UNAVAILABLE(EXIT_PRECONDITION),
    PROFILE_STALE(EXIT_PRECONDITION),
    PROFILE_INVALID(EXIT_FAILED),
    PROFILE_INSTALL_FAILED(EXIT_FAILED),
    APP_MISSING(EXIT_NOT_FOUND),
    VERSION_MISSING(EXIT_NOT_FOUND),
    LOCALIZATION_MISSING(EXIT_NOT_FOUND),
    BUILD_MISSING(EXIT_NOT_FOUND),
    BUILD_NOT_READY(EXIT_PRECONDITION),
    RELEASE_INPUT_INVALID(EXIT_PRECONDITION),
    SUBMISSION_NOT_READY(EXIT_PRECONDITION),
    VERSION_NOT_EDITABLE(EXIT_PRECONDITION),
    UPLOAD_REFUSED(EXIT_FAILED),
    UPLOAD_FAILED(EXIT_FAILED),
    SCREENSHOTS_NOT_DELIVERED(EXIT_FAILED),
    COMMAND_FAILED(EXIT_FAILED),
}

class ProvisioningException(
    val code: ErrorCode,
    message: String,
    val hint: String? = null,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
