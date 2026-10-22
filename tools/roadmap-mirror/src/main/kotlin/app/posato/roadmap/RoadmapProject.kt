package app.posato.roadmap

/**
 * The public "Posato roadmap" project that mirrors `docs/tasks/release-roadmap.md`, and the ids of the fields this
 * tool writes, kept here and nowhere else. After a field or option changes in the GitHub interface, read the new ids
 * with `gh project field-list 1 --owner @me --format json`.
 */
object RoadmapProject {
    const val ID = "PVT_kwHOAB0Ak84Bj5GQ"

    const val STATUS_FIELD = "PVTSSF_lAHOAB0Ak84Bj5GQzhirxkc"
    val STATUS = mapOf("todo" to "f75ad846", "in-progress" to "47fc9ee4", "done" to "98236657")

    /** A text field holding `#<number>`. */
    const val PR_FIELD = "PVTF_lAHOAB0Ak84Bj5GQzhirxt4"

    const val RELEASE_FIELD = "PVTSSF_lAHOAB0Ak84Bj5GQzhirxrg"
    val RELEASE = mapOf(
        "1.1" to "a373a2a0",
        "1.2" to "5e92a87f",
        "1.3" to "93a915ea",
        "1.4" to "2714e1a7",
        "1.5" to "4d38075a",
        "backlog" to "0b94f86b",
    )

    const val CLASS_FIELD = "PVTSSF_lAHOAB0Ak84Bj5GQzhirxrs"
    val CLASS = mapOf("delivery" to "3eeea99e", "discovery" to "d41bd04e", "backlog" to "f8157b0d")

    const val EPIC_FIELD = "PVTSSF_lAHOAB0Ak84Bj5GQzhirxr0"
    val EPIC = mapOf(
        "Sessions and enforcement" to "74bda1c5",
        "Onboarding" to "c471482c",
        "Target management" to "65ef72a4",
        "Platform coverage" to "85bef049",
        "Release readiness" to "06c9f04e",
        "Verification" to "ac6b7f4c",
        "Notifications" to "fad3fd53",
        "Schedules" to "d21cbc2f",
        "Product discovery" to "3d8f55f8",
        "Portable synchronization" to "27e3351e",
    )
}
