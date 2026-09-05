package app.posato.provisioning.local

import java.time.Instant

/** Both spellings occur: an iOS profile carries `application-identifier`, a macOS one the prefixed form. */
private val APPLICATION_IDENTIFIER = Regex("""<key>(?:com\.apple\.)?application-identifier</key>\s*<string>([^<]+)</string>""")
private val TEAM_IDENTIFIER = Regex("""<key>TeamIdentifier</key>\s*<array>\s*<string>([A-Za-z0-9]{10})</string>""")
private val EXPIRATION_DATE = Regex("""<key>ExpirationDate</key>\s*<date>([^<]+)</date>""")
private val PROFILE_UUID = Regex("""<key>UUID</key>\s*<string>([^<]+)</string>""")
private val PLATFORM_BLOCK = Regex("""<key>Platform</key>\s*<array>(.*?)</array>""", RegexOption.DOT_MATCHES_ALL)
private val PLATFORM_ENTRY = Regex("""<string>([^<]+)</string>""")

/**
 * The fields of a decoded provisioning profile that decide whether it is the right profile.
 *
 * A downloaded profile is checked against these before it is installed, because a profile for the wrong App ID,
 * the wrong team, or an expired one fails much later and much less clearly: at codesign time, or at run time on a
 * device, with no message pointing back at the file.
 */
data class DecodedProfile(
    val applicationIdentifier: String?,
    val teamIdentifier: String?,
    val expiresAt: Instant?,
    val uuid: String?,
    val platforms: List<String>,
) {
    companion object {
        fun decode(plist: String): DecodedProfile? {
            if (!plist.contains("<key>ExpirationDate</key>") && !plist.contains("application-identifier")) return null
            return DecodedProfile(
                applicationIdentifier = APPLICATION_IDENTIFIER.find(plist)?.groupValues?.get(1),
                teamIdentifier = TEAM_IDENTIFIER.find(plist)?.groupValues?.get(1),
                expiresAt = EXPIRATION_DATE.find(plist)?.groupValues?.get(1)?.let { value ->
                    runCatching { Instant.parse(value) }.getOrNull()
                },
                uuid = PROFILE_UUID.find(plist)?.groupValues?.get(1),
                platforms = PLATFORM_BLOCK.find(plist)
                    ?.groupValues
                    ?.get(1)
                    ?.let { block -> PLATFORM_ENTRY.findAll(block).map { entry -> entry.groupValues[1] }.toList() }
                    .orEmpty(),
            )
        }
    }
}
