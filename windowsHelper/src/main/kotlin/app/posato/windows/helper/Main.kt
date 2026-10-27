package app.posato.windows.helper

import kotlin.system.exitProcess

/**
 * `serve` runs the service under the Service Control Manager; `install --user <name>`, `uninstall`, and `purge` are
 * the MSI's deferred steps, run as the system. The installing user comes from the configuration that `install` wrote
 * into the protected folder.
 */
fun main(args: Array<String>) {
    val code = when (args.firstOrNull()) {
        "serve" -> serve()
        "install" -> args.takeIf { it.size == INSTALL_ARGUMENTS && it[1] == "--user" }?.let { Installer().install(it[2]) } ?: USAGE
        "uninstall" -> Installer().uninstall()
        "purge" -> Installer().purge()
        else -> USAGE
    }
    exitProcess(code)
}

private fun serve(): Int {
    val folder = HelperFolder()
    val config = folder.read(folder.config)?.let(HelperConfig::decode) ?: return MISSING_CONFIG
    return ServiceHost(HelperService(folder, config)).run()
}

private const val INSTALL_ARGUMENTS = 3
private const val USAGE = 2
private const val MISSING_CONFIG = 6
