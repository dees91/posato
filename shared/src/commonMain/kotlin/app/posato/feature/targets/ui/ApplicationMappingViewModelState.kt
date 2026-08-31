package app.posato.feature.targets.ui

import app.posato.feature.targets.data.LocalApplicationMappingsLoadFailure
import app.posato.feature.targets.data.LocalApplicationRemovalFailure
import app.posato.feature.targets.data.LocalApplicationSelectionFailure
import app.posato.feature.targets.data.LocalApplicationSelectionRejection

internal fun LocalApplicationMappingsLoadFailure.toUiFailure(): ApplicationMappingFailure {
    return when (this) {
        LocalApplicationMappingsLoadFailure.STORAGE -> ApplicationMappingFailure.LOAD_FAILED
        LocalApplicationMappingsLoadFailure.CORRUPTION -> ApplicationMappingFailure.CORRUPTED_MAPPINGS
    }
}

internal fun LocalApplicationSelectionRejection.toUiFailure(): ApplicationMappingFailure {
    return when (this) {
        LocalApplicationSelectionRejection.SELF -> ApplicationMappingFailure.SELF_SELECTION
        LocalApplicationSelectionRejection.INVALID_OR_UNSIGNED -> ApplicationMappingFailure.INVALID_OR_UNSIGNED
        LocalApplicationSelectionRejection.CAPACITY -> ApplicationMappingFailure.CAPACITY
    }
}

internal fun LocalApplicationSelectionFailure.toUiFailure(): ApplicationMappingFailure {
    return when (this) {
        LocalApplicationSelectionFailure.PICKER -> ApplicationMappingFailure.PICKER_FAILED
        LocalApplicationSelectionFailure.STORAGE -> ApplicationMappingFailure.SAVE_FAILED
    }
}

internal fun LocalApplicationRemovalFailure.toUiFailure(): ApplicationMappingFailure {
    return when (this) {
        LocalApplicationRemovalFailure.STORAGE -> ApplicationMappingFailure.SAVE_FAILED
    }
}
