package app.posato.linux.helper

import java.nio.file.Files
import kotlin.system.exitProcess

/**
 * `serve` runs the service under systemd; `cleanup` restores `/etc/hosts` and forgets the state when the package is
 * removed. The installing person comes from the root-owned configuration that the install step wrote.
 */
fun main(args: Array<String>) {
    val paths = HelperPaths()
    val person = readPerson(paths) ?: run {
        System.err.println("posato-helper: missing or invalid ${paths.config}")
        exitProcess(2)
    }
    val service = HelperService(paths, person)
    when (args.firstOrNull()) {
        "serve" -> service.serve()
        "cleanup" -> service.cleanup()
        else -> exitProcess(2)
    }
}

private fun readPerson(paths: HelperPaths): Person? {
    if (!Files.isRegularFile(paths.config)) return null
    val values = Files.readAllLines(paths.config).mapNotNull { line -> line.split('=', limit = 2).takeIf { it.size == 2 } }
        .associate { (key, value) -> key.trim() to value.trim() }
    val name = values["user"]?.takeIf { it.matches(Regex("[a-z_][a-z0-9_-]{0,31}")) } ?: return null
    val uid = values["uid"]?.toIntOrNull()?.takeIf { it > 0 } ?: return null
    return Person(name, uid)
}
