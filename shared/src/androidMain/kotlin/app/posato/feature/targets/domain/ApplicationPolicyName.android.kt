package app.posato.feature.targets.domain

import java.text.Normalizer

internal actual fun normalizeApplicationPolicyNameNfc(value: String): String {
    return Normalizer.normalize(value, Normalizer.Form.NFC)
}
