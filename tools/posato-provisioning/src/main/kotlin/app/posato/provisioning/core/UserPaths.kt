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

    companion object {
        fun discover(): UserPaths = UserPaths(Path.of(System.getProperty("user.home")))
    }
}
