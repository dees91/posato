package app.posato.desktop.macos

internal fun interface MacOsApplicationPicker {
    fun selectApplications(): MacOsApplicationPickerResult
}

internal sealed interface MacOsApplicationPickerResult {
    data class Success(
        val applications: List<SelectedMacOsApplication>,
    ) : MacOsApplicationPickerResult {
        override fun toString(): String {
            return "MacOsApplicationPickerResult.Success(redacted)"
        }
    }

    data object Cancelled : MacOsApplicationPickerResult

    data object SelfSelection : MacOsApplicationPickerResult

    data object InvalidOrUnsigned : MacOsApplicationPickerResult

    data object CapacityExceeded : MacOsApplicationPickerResult

    data object Failure : MacOsApplicationPickerResult
}

internal class SelectedMacOsApplication(
    val displayName: String,
    val designatedRequirement: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        return other is SelectedMacOsApplication &&
            displayName == other.displayName &&
            designatedRequirement.contentEquals(other.designatedRequirement)
    }

    override fun hashCode(): Int {
        return 31 * displayName.hashCode() + designatedRequirement.contentHashCode()
    }

    override fun toString(): String {
        return "SelectedMacOsApplication(redacted)"
    }
}
