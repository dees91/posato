package app.posato.provisioning.core

import java.nio.file.Path

/**
 * The two directories outside the checkout that hold provisioning material.
 *
 * Both are the maintainer's, never the repository's: nothing this tool downloads may become a tracked file. The
 * Xcode directory is the one modern Xcode reads; the legacy `~/Library/MobileDevice/Provisioning Profiles` is not
 * written, because Xcode 16 and later no longer look there.
 */
class UserPaths(
    val home: Path
) {
    val posatoDeveloperDirectory: Path = home.resolve("Library").resolve("Developer").resolve("Posato")
    val xcodeProfilesDirectory: Path = home
        .resolve("Library")
        .resolve("Developer")
        .resolve("Xcode")
        .resolve("UserData")
        .resolve("Provisioning Profiles")

    /**
     * A path a maintainer can act on that names no person.
     *
     * A message telling someone which file to import is useless once redaction has replaced the home directory in
     * it, and an absolute path under the home directory must not reach an envelope that gets pasted into a record.
     * The tilde form satisfies both.
     */
    fun display(path: Path): String = if (path.startsWith(home)) "~/${home.relativize(path)}" else path.toString()

    companion object {
        fun discover(): UserPaths = UserPaths(Path.of(System.getProperty("user.home")))
    }
}
