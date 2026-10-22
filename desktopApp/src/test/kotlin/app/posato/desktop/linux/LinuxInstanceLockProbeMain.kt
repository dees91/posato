package app.posato.desktop.linux

import java.nio.file.Path

object LinuxInstanceLockProbeMain {
    @JvmStatic
    fun main(arguments: Array<String>) {
        val lock = LinuxInstanceLock(Path.of(arguments[0]))
        println(if (lock.tryAcquire()) "held" else "refused")
        System.out.flush()
        System.`in`.read()
        lock.close()
    }
}
