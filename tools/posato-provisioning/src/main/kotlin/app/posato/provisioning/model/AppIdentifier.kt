package app.posato.provisioning.model

import app.posato.provisioning.core.ErrorCode
import app.posato.provisioning.core.ProvisioningException

enum class ApplePlatform(
    val ascName: String
) {
    IOS("IOS"),
    MACOS("MAC_OS"),
}

/**
 * The five Posato App IDs and the development profile each one needs.
 *
 * The set is closed rather than configurable because the account holds exactly these App IDs, and a tool that
 * accepted any identifier could install a profile for something Posato does not ship. The macOS sync profile name
 * matches the file `SYNC-006` created by hand, so a run overwrites that file in place and the configured
 * `posato.macos.syncProvisioningProfile` keeps working without being edited.
 */
enum class AppIdentifier(
    val bundleId: String,
    val platform: ApplePlatform,
    val profileName: String,
    val fileExtension: String,
    val profileType: String,
) {
    IOS_APPLICATION("app.posato.ios", ApplePlatform.IOS, "Posato_iOS_App_Development", "mobileprovision", "IOS_APP_DEVELOPMENT"),
    IOS_ACTIVITY_MONITOR(
        "app.posato.ios.activitymonitor",
        ApplePlatform.IOS,
        "Posato_iOS_ActivityMonitor_Development",
        "mobileprovision",
        "IOS_APP_DEVELOPMENT",
    ),
    MACOS_APPLICATION("app.posato.macos", ApplePlatform.MACOS, "Posato_macOS_App_Development", "provisionprofile", "MAC_APP_DEVELOPMENT"),
    MACOS_HELPER(
        "app.posato.macos.helper",
        ApplePlatform.MACOS,
        "Posato_macOS_Helper_Development",
        "provisionprofile",
        "MAC_APP_DEVELOPMENT",
    ),
    MACOS_SYNC("app.posato.macos.sync", ApplePlatform.MACOS, "Posato_macOS_Sync_Development", "provisionprofile", "MAC_APP_DEVELOPMENT"),
    ;

    val fileName: String get() = "$profileName.$fileExtension"

    companion object {
        fun of(bundleId: String): AppIdentifier = entries.firstOrNull { entry -> entry.bundleId == bundleId }
            ?: throw ProvisioningException(
                ErrorCode.USAGE,
                "$bundleId is not one of the Posato App IDs.",
                "Use one of: ${entries.joinToString(", ") { entry -> entry.bundleId }}.",
            )
    }
}
