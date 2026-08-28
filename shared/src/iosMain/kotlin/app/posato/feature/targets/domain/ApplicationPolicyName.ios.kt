@file:Suppress("CAST_NEVER_SUCCEEDS")

package app.posato.feature.targets.domain

import platform.Foundation.NSString
import platform.Foundation.precomposedStringWithCanonicalMapping

internal actual fun normalizeApplicationPolicyNameNfc(value: String): String {
    return (value as NSString).precomposedStringWithCanonicalMapping
}
