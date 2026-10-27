package app.posato.desktop.resident

import java.nio.file.Path

object InstanceLockProbeMain {
    @JvmStatic
    fun main(arguments: Array<String>) {
        val lock = InstanceLock(Path.of(arguments[0]))
        println(if (lock.tryAcquire()) "held" else "refused")
        System.out.flush()
        System.`in`.read()
        lock.close()
    }
}
