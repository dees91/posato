package app.posato.roadmap

/**
 * The public "Posato roadmap" project that mirrors `docs/tasks/release-roadmap.md`, and the ids of the fields this
 * tool writes, kept here and nowhere else. After a field or option changes in the GitHub interface, read the new ids
 * with `gh project field-list 1 --owner @me --format json`.
 */
object RoadmapProject {
    /** An option of a single-select field: the name GitHub shows and reads back, and the id a write takes. */
    data class Option(
        val name: String,
        val id: String,
    )

    /** The field names a read-back reports, for the fields this tool writes. */
    const val STATUS_NAME = "Status"
    const val PR_NAME = "PR"
    const val RELEASE_NAME = "Release"
    const val CLASS_NAME = "Class"
    const val EPIC_NAME = "Epic"

    const val ID = "PVT_kwHOAB0Ak84Bj5GQ"

    const val STATUS_FIELD = "PVTSSF_lAHOAB0Ak84Bj5GQzhirxkc"
    val STATUS = mapOf("todo" to Option("Todo", "f75ad846"), "in-progress" to Option("In Progress", "47fc9ee4"), "done" to Option("Done", "98236657"))

    /** A text field holding `#<number>`. */
    const val PR_FIELD = "PVTF_lAHOAB0Ak84Bj5GQzhirxt4"

    const val RELEASE_FIELD = "PVTSSF_lAHOAB0Ak84Bj5GQzhirxrg"
    val RELEASE = mapOf(
        "1.1" to Option("1.1", "a373a2a0"),
        "1.2" to Option("1.2", "5e92a87f"),
        "1.3" to Option("1.3", "93a915ea"),
        "1.4" to Option("1.4", "2714e1a7"),
        "1.5" to Option("1.5", "4d38075a"),
        "1.6" to Option("1.6", "edafbf71"),
        "backlog" to Option("Backlog", "0b94f86b"),
    )

    const val CLASS_FIELD = "PVTSSF_lAHOAB0Ak84Bj5GQzhirxrs"
    val CLASS = mapOf(
        "delivery" to Option("delivery", "3eeea99e"),
        "discovery" to Option("discovery", "d41bd04e"),
        "backlog" to Option("backlog", "f8157b0d"),
    )

    const val EPIC_FIELD = "PVTSSF_lAHOAB0Ak84Bj5GQzhirxr0"
    val EPIC = mapOf(
        "Sessions and enforcement" to Option("Sessions and enforcement", "74bda1c5"),
        "Onboarding" to Option("Onboarding", "c471482c"),
        "Target management" to Option("Target management", "65ef72a4"),
        "Platform coverage" to Option("Platform coverage", "85bef049"),
        "Release readiness" to Option("Release readiness", "06c9f04e"),
        "Verification" to Option("Verification", "ac6b7f4c"),
        "Notifications" to Option("Notifications", "fad3fd53"),
        "Schedules" to Option("Schedules", "d21cbc2f"),
        "Product discovery" to Option("Product discovery", "3d8f55f8"),
        "Portable synchronization" to Option("Portable synchronization", "27e3351e"),
    )
}
