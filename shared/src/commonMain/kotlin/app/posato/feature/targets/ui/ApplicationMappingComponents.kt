package app.posato.feature.targets.ui

import androidx.compose.runtime.Composable
import app.posato.feature.targets.data.LocalApplicationMappingsAccess
import app.posato.generated.resources.Res
import app.posato.generated.resources.application_group_mapping_required
import app.posato.generated.resources.application_mapping_access_denied
import app.posato.generated.resources.application_mapping_access_required
import app.posato.generated.resources.application_mapping_access_restricted
import app.posato.generated.resources.application_mapping_access_unavailable
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun TargetsUiState.applicationMappingSupportingText(): String? {
    if (isApplicationMappingLoading || hasApplicationMappingLoadFailure) {
        return null
    }

    return applicationMappingAccessMessage()
        ?: if (applicationMappings.isEmpty()) stringResource(Res.string.application_group_mapping_required) else null
}

@Composable
private fun TargetsUiState.applicationMappingAccessMessage(): String? {
    return when {
        !isApplicationMappingAvailable -> {
            stringResource(Res.string.application_mapping_access_unavailable)
        }

        applicationMappingsAccess == LocalApplicationMappingsAccess.AUTHORIZATION_REQUIRED -> {
            stringResource(Res.string.application_mapping_access_required)
        }

        applicationMappingsAccess == LocalApplicationMappingsAccess.AUTHORIZATION_DENIED -> {
            stringResource(Res.string.application_mapping_access_denied)
        }

        applicationMappingsAccess == LocalApplicationMappingsAccess.RESTRICTED -> {
            stringResource(Res.string.application_mapping_access_restricted)
        }

        else -> {
            null
        }
    }
}
