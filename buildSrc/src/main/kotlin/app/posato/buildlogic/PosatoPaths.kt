package app.posato.buildlogic

object PosatoPaths {
    fun expandHome(path: String): String {
        return if (path.startsWith("~/")) System.getProperty("user.home") + path.removePrefix("~") else path
    }
}
