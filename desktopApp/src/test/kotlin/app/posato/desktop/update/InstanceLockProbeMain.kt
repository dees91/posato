package app.posato.desktop.update

import java.nio.file.Path

object InstanceLockProbeMain {
    @JvmStatic
    fun main(arguments: Array<String>) {
        val lock = FileInstanceLock(Path.of(arguments[0]))
        val acquired = when (arguments[1]) {
            "shared" -> lock.acquireShared()
            "admit" -> lock.acquireShared() && lock.tryUpgradeForAdmission()
            else -> false
        }
        println(if (acquired) "held" else "refused")
        System.out.flush()
        System.`in`.read()
        lock.close()
    }
}
