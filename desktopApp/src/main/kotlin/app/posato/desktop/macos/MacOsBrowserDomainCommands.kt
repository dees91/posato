package app.posato.desktop.macos

internal interface MacOsBrowserDomainCommands {
    fun configureBrowserDomains(
        domains: List<String>,
        sessionEndEpochMilliseconds: Long?,
    ): BrowserDomainConfigureResponse

    fun apply(port: UShort): HelperResult

    fun applyWithGrant(port: UShort): HelperResult

    fun grantState(): HelperGrantState

    fun restore(): HelperResult

    fun reconcileUnknown(): HelperResult
}
