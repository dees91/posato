package app.posato.control.vm

/** Reads `networksetup -getproxybypassdomains`: one entry per line, or a sentence when the list is empty. */
internal fun parseBypassDomains(output: String): List<String> = output.lines()
    .map { line -> line.trim() }
    .filter { line -> line.isNotEmpty() && !line.startsWith(EMPTY_BYPASS_PREFIX) }

private const val EMPTY_BYPASS_PREFIX = "There aren't any bypass domains set on "
