package app.posato.feature.onboarding

import app.posato.feature.onboarding.data.LocalSetupStore

internal class OnboardingDependencies(
    val setupStore: LocalSetupStore,
    val applicationAccess: ApplicationAccessPort,
    val macHelper: MacHelperPort,
    val permissionPlatform: OnboardingPermissionPlatform,
)
