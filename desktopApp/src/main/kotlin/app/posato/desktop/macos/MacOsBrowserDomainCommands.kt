package app.posato.desktop.macos

import app.posato.feature.enforcement.DomainWindowPlan

internal interface MacOsBrowserDomainCommands {
    fun configureBrowserDomains(
        domains: List<String>,
        sessionEndEpochMilliseconds: Long?,
        windows: List<DomainWindowPlan> = emptyList(),
    ): BrowserDomainConfigureResponse

    fun apply(port: UShort): HelperResult

    fun applyWithGrant(port: UShort): HelperResult

    fun grantState(): HelperGrantState

    fun restore(): HelperResult

    fun reconcileUnknown(): HelperResult
}
