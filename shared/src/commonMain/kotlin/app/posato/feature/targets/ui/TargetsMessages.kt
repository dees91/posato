package app.posato.feature.targets.ui

import app.posato.generated.resources.Res
import app.posato.generated.resources.application_entry_error_empty
import app.posato.generated.resources.application_entry_error_invalid
import app.posato.generated.resources.application_entry_error_too_long
import app.posato.generated.resources.application_group_description
import app.posato.generated.resources.application_mapping_capacity
import app.posato.generated.resources.application_mapping_corruption
import app.posato.generated.resources.application_mapping_corruption_clear_failed
import app.posato.generated.resources.application_mapping_invalid
import app.posato.generated.resources.application_mapping_load_failed
import app.posato.generated.resources.application_mapping_picker_failed
import app.posato.generated.resources.application_mapping_save_failed
import app.posato.generated.resources.application_mapping_self
import app.posato.generated.resources.application_mapping_unsupported
import app.posato.generated.resources.domain_input_description
import app.posato.generated.resources.entry_error_duplicate
import app.posato.generated.resources.entry_error_empty
import app.posato.generated.resources.entry_error_invalid
import app.posato.generated.resources.entry_error_limit
import app.posato.generated.resources.entry_error_too_long
import app.posato.generated.resources.operation_error_corruption
import app.posato.generated.resources.operation_error_load
import app.posato.generated.resources.operation_error_save
import app.posato.generated.resources.operation_error_update
import org.jetbrains.compose.resources.StringResource

internal fun ExactDomainEntryFailure?.domainMessage(): StringResource {
    return when (this) {
        ExactDomainEntryFailure.EMPTY -> Res.string.entry_error_empty
        ExactDomainEntryFailure.TOO_LONG -> Res.string.entry_error_too_long
        ExactDomainEntryFailure.INVALID_DOMAIN -> Res.string.entry_error_invalid
        ExactDomainEntryFailure.DUPLICATE -> Res.string.entry_error_duplicate
        ExactDomainEntryFailure.LIMIT_REACHED -> Res.string.entry_error_limit
        null -> Res.string.domain_input_description
    }
}

internal fun ApplicationPolicyEntryFailure?.applicationMessage(): StringResource {
    return when (this) {
        ApplicationPolicyEntryFailure.EMPTY -> Res.string.application_entry_error_empty
        ApplicationPolicyEntryFailure.TOO_LONG -> Res.string.application_entry_error_too_long
        ApplicationPolicyEntryFailure.INVALID_CHARACTERS -> Res.string.application_entry_error_invalid
        null -> Res.string.application_group_description
    }
}

internal fun TargetsOperationFailure?.operationMessage(): StringResource {
    return when (this) {
        TargetsOperationFailure.REVISION_CONFLICT -> Res.string.operation_error_update

        TargetsOperationFailure.CORRUPTED_POLICY -> Res.string.operation_error_corruption

        TargetsOperationFailure.SAVE_FAILED -> Res.string.operation_error_save

        TargetsOperationFailure.LOAD_FAILED,
        null -> Res.string.operation_error_load
    }
}

internal fun ApplicationMappingFailure.applicationMappingMessage(): StringResource {
    return when (this) {
        ApplicationMappingFailure.LOAD_FAILED -> Res.string.application_mapping_load_failed
        ApplicationMappingFailure.CORRUPTED_MAPPINGS -> Res.string.application_mapping_corruption
        ApplicationMappingFailure.CORRUPTED_CLEAR_FAILED -> Res.string.application_mapping_corruption_clear_failed
        ApplicationMappingFailure.PICKER_FAILED -> Res.string.application_mapping_picker_failed
        ApplicationMappingFailure.SAVE_FAILED -> Res.string.application_mapping_save_failed
        ApplicationMappingFailure.SELF_SELECTION -> Res.string.application_mapping_self
        ApplicationMappingFailure.INVALID_OR_UNSIGNED -> Res.string.application_mapping_invalid
        ApplicationMappingFailure.UNSUPPORTED_SELECTION -> Res.string.application_mapping_unsupported
        ApplicationMappingFailure.CAPACITY -> Res.string.application_mapping_capacity
    }
}
