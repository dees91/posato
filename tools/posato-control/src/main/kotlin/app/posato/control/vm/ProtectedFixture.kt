package app.posato.control.vm

import app.posato.control.core.ControlException
import app.posato.control.core.ControlJson
import app.posato.control.core.ErrorCode
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

/**
 * An Apple Account whose Posato iCloud workspace a later task needs as it is, such as the long history that
 * `SYNC-022` reproduces on. An account has one Posato workspace, its CloudKit zone, so the account names the zone.
 * [account] is the `AccountID` that the guest's `MobileMeAccounts` reports.
 */
@Serializable
data class ProtectedFixture(
    val account: String,
    val fixture: String,
    val reason: String = "",
)

/** The option that removes a protected workspace anyway. */
internal const val DESTROY_FIXTURE_OPTION = "--destroy-fixture"

/**
 * The machine-wide list, beside the driver's VM state in Tart's home: every worktree and session on this Mac shares
 * the test Apple Account, so a list in one checkout would not stop a run in another. It holds account names, so it
 * stays out of Git.
 */
internal fun protectedFixturesFile(): Path = stateRoot().resolve(PROTECTED_FIXTURES_FILE)

private const val PROTECTED_FIXTURES_FILE = "protected-fixtures.json"

/** An absent file protects nothing; an unreadable one refuses, since it was written to protect something. */
internal fun loadProtectedFixtures(file: Path): List<ProtectedFixture> {
    if (!file.exists()) return emptyList()
    return try {
        ControlJson.lenient.decodeFromString(ListSerializer(ProtectedFixture.serializer()), file.readText())
    } catch (exception: IllegalArgumentException) {
        // Also a SerializationException, which extends it.
        throw unreadable(file, exception)
    } catch (exception: IOException) {
        throw unreadable(file, exception)
    }
}

private fun unreadable(
    file: Path,
    cause: Exception,
) = ControlException(
    ErrorCode.REFUSED_WITHOUT_CONFIRMATION,
    "The protected fixtures list at $file cannot be read: ${cause.message}.",
    "Fix the file (a JSON array of {account, fixture, reason}), or pass $DESTROY_FIXTURE_OPTION.",
    cause,
)

/**
 * Why `flow icloud remove` must not run in a guest signed in to the accounts [signedIn] reads, or null when it may.
 * [signedIn] is read only when a fixture is listed and [destroyFixture] is off; it returns null when the guest's
 * accounts could not be read, which refuses too, since the guest may be signed in to a fixture. A verification run
 * once removed the long-history zone another task needed (2026-10-21).
 */
internal fun protectedFixtureRefusal(
    fixtures: List<ProtectedFixture>,
    destroyFixture: Boolean,
    signedIn: () -> List<String>?,
): ControlException? {
    if (destroyFixture || fixtures.isEmpty()) return null
    val accounts = signedIn()
    if (accounts == null) {
        return ControlException(
            ErrorCode.REFUSED_WITHOUT_CONFIRMATION,
            "The guest's Apple Account could not be read, and protected fixtures are listed: " +
                "${fixtures.joinToString { it.fixture }}.",
            "Check the guest's iCloud sign-in, or pass $DESTROY_FIXTURE_OPTION if no fixture is at stake.",
        )
    }
    // Account IDs are email addresses, so their case does not tell two apart.
    val hit = fixtures.firstOrNull { fixture -> accounts.any { it.equals(fixture.account, ignoreCase = true) } } ?: return null
    return ControlException(
        ErrorCode.REFUSED_WITHOUT_CONFIRMATION,
        "The guest's iCloud workspace is the protected fixture '${hit.fixture}'" +
            (if (hit.reason.isBlank()) "." else ": ${hit.reason}."),
        "Leave the workspace linked and destroy the clone with `vm destroy --keep-workspace`, or pass " +
            "$DESTROY_FIXTURE_OPTION when the fixture may go, then remove its entry from the protected fixtures list.",
    )
}

/** The `AccountID`s the guest is signed in to, or null when they cannot be read. */
internal fun signedInAccounts(
    tart: Tart,
    clone: String,
): List<String>? {
    val output = tart.exec(clone, "/usr/bin/defaults read MobileMeAccounts Accounts")
    // `defaults` exits 1 when the domain does not exist: no account was ever signed in.
    if (output.exitCode != 0) return if (output.stderr.contains("does not exist")) emptyList() else null
    return ACCOUNT_ID.findAll(output.stdout).map { match -> match.groupValues[1] }.toList()
}

internal val ACCOUNT_ID = Regex("AccountID\\s*=\\s*\"?([^\";\\s]+)\"?;")
