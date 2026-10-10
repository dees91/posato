package app.posato.control.cli

import app.posato.control.vm.LeaseState
import app.posato.control.vm.Tart
import app.posato.control.vm.VmLine
import app.posato.control.vm.describeCloneOwner
import app.posato.control.vm.leaseState
import app.posato.control.vm.readCloneOwner
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.nio.file.Path
import kotlin.io.path.exists

/**
 * Lists every line's clone with the worktree that holds it, so parallel sessions can see who uses which guest before
 * they create one. A clone counts as held while it runs or while its worktree exists; a stopped clone left by a
 * removed worktree is stale, and only `vm destroy` removes it.
 */
class VmLeasesCommand : ControlCommand("leases", "List each VM line's clone, who holds it, and whether it is free, held, or stale.") {
    override fun execute(session: Session): JsonElement {
        val vms = Tart(session.context).list()
        return buildJsonObject {
            put("running", vms.count { it.running })
            putJsonArray("lines") {
                VmLine.entries.forEach { line ->
                    val vm = vms.firstOrNull { it.name == line.cloneName }
                    val owner = vm?.let { readCloneOwner(line.cloneName) }
                    val worktreeExists = owner?.let { Path.of(it.worktree).resolve(".git").exists() } ?: false
                    val state = leaseState(vm != null, vm?.running == true, owner, worktreeExists)
                    addJsonObject {
                        put("line", line.id)
                        put("clone", line.cloneName)
                        put("state", state.name.lowercase())
                        if (vm != null) put("running", vm.running)
                        owner?.let {
                            put("worktree", it.worktree)
                            put("runId", it.runId)
                            put("createdAt", it.createdAt)
                        }
                        if (state != LeaseState.FREE) put("detail", describeCloneOwner(line.cloneName, owner, session.layout.root))
                    }
                }
            }
        }
    }
}
