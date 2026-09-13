package app.posato.feature.enforcement

public enum class ExpiryDisplacementOutcome {
    ABSENT,
    PRESENT,
    FAILED,
}

public class ExpiryDisplacement(
    public val outcome: ExpiryDisplacementOutcome,
    public val sessionId: String?,
) {
    override fun toString(): String {
        return "ExpiryDisplacement(redacted)"
    }
}
