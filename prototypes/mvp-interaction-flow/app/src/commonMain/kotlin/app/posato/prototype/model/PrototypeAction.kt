package app.posato.prototype.model

import kotlinx.collections.immutable.PersistentList

sealed interface PrototypeAction {
    val label: String
}

data object ResetPrototype : PrototypeAction {
    override val label: String = "Reset to first visit"
}

enum class SetupAction(
    override val label: String
) : PrototypeAction {
    ShowPrivacy("Understand purpose and privacy"),
    SyncWithICloud("Sync with iCloud"),
    DiscoverEmptyWorkspace("Discover an empty workspace"),
    DiscoverDelayedKey("Discover a delayed workspace key"),
    KeyArrived("Receive the existing key"),
    GrantPermission("Allow on this device"),
    AddExampleDomain("Add a shared website"),
    MapExampleApplication("Choose a local application"),
    FinishOnboarding("Finish setup")
}

enum class SessionAction(
    override val label: String
) : PrototypeAction {
    OpenSetup("Choose session duration"),
    ReturnToSession("Return to session"),
    Review("Review session"),
    Start("Start session"),
    OpenBlocked("Open a selected item"),
    OpenPosato("Open Posato"),
    RequestEarlyEnd("End session early"),
    CancelEarlyEnd("Keep session"),
    ConfirmEarlyEnd("Confirm early end"),
    Expire("Reach the selected end")
}

data class SetDuration(
    val input: String
) : PrototypeAction {
    override val label: String = "Choose $input minutes"
}

enum class SyncAction(
    override val label: String
) : PrototypeAction {
    Start("Sync now"),
    Retry("Retry sync"),
    Succeed("Complete the local sync"),
    Fail("Fail the local sync")
}

enum class RecoveryAction(
    override val label: String
) : PrototypeAction {
    RevokePermission("Revoke local permission"),
    RepairPermission("Restore local permission"),
    RemoveMapping("Lose the local app mapping"),
    RemapApplication("Choose a replacement app")
}

sealed interface ItemAction : PrototypeAction {
    data object OpenItems : ItemAction {
        override val label: String = "Manage paused items"
    }

    data object CloseItems : ItemAction {
        override val label: String = "Done"
    }

    data class OpenDomain(
        val original: String? = null
    ) : ItemAction {
        override val label: String = "Edit website"
    }

    data class SaveDomain(
        val input: String
    ) : ItemAction {
        override val label: String = "Save website"
    }

    data class RemoveDomain(
        val domain: String
    ) : ItemAction {
        override val label: String = "Remove website"
    }

    data object OpenApplications : ItemAction {
        override val label: String = "Choose applications"
    }

    data class SaveApplications(
        val names: PersistentList<String>
    ) : ItemAction {
        override val label: String = "Save applications"
    }

    data class RemoveApplication(
        val name: String
    ) : ItemAction {
        override val label: String = "Remove application"
    }

    data object Cancel : ItemAction {
        override val label: String = "Cancel"
    }
}
