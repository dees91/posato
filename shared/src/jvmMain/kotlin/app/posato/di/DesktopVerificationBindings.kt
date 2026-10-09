package app.posato.di

import app.posato.feature.sync.bootstrap.AppleSync
import app.posato.feature.sync.macos.MaintenanceCompanionTransport
import app.posato.feature.sync.macos.applicationBundleRoot
import app.posato.feature.sync.macos.verification.DesktopVerificationSeams
import app.posato.feature.sync.macos.verification.MacOsDesktopVerificationSeams
import dev.zacsweers.metro.Provides

/** The verification seams of the ADR 0007 amendment of 2026-10-09; inert without the development package key. */
internal interface DesktopVerificationBindings {
    @Provides
    fun provideVerificationSeams(
        appleSync: AppleSync,
        companion: MaintenanceCompanionTransport,
    ): DesktopVerificationSeams {
        return MacOsDesktopVerificationSeams(appleSync.bootstrap, companion, ::applicationBundleRoot)
    }
}
