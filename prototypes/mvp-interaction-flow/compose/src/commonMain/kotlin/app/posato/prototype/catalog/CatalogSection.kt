package app.posato.prototype.catalog

enum class CatalogSection(
    val label: String,
    val description: String
) {
    Foundations("Foundations", "The palette, type, spacing, and open interval that give this study its character."),
    Controls("Controls", "Actions, choices, and selection. State stays with the caller."),
    Forms("Forms", "Editable text, validation feedback, and local selection without product services."),
    Feedback("Feedback", "Truthful status, bounded repair, and supporting session information."),
    Patterns("Product patterns", "Reusable pieces composed into familiar moments from the interaction study."),
    Workbench("Prototype tools", "Inspection components, preview chrome, and guided-action controls."),
}
