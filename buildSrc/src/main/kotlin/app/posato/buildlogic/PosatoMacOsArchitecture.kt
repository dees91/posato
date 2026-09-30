package app.posato.buildlogic

import org.gradle.api.GradleException

enum class PosatoMacOsArchitecture(
    val propertyValue: String,
    val machOName: String,
    val skikoName: String,
    val sqliteDirectory: String,
) {
    ARM64("arm64", "arm64", "arm64", "aarch64"),
    X86_64("x86_64", "x86_64", "x64", "x86_64"),
    ;

    val clangTarget: String
        get() = "$machOName-apple-macos$MINIMUM_SYSTEM_VERSION"

    val swiftTriple: String
        get() = "$machOName-apple-macosx$MINIMUM_SYSTEM_VERSION"

    val foreign: PosatoMacOsArchitecture
        get() = if (this == ARM64) X86_64 else ARM64

    companion object {
        const val PROPERTY = "posatoMacOsArchitecture"
        const val MINIMUM_SYSTEM_VERSION = "13.0"

        fun resolve(value: String?): PosatoMacOsArchitecture {
            if (value == null) return ARM64
            return entries.firstOrNull { architecture -> architecture.propertyValue == value }
                ?: throw GradleException("-P$PROPERTY must be arm64 or x86_64, not '$value'.")
        }
    }
}
