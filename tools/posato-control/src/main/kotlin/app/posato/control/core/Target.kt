package app.posato.control.core

enum class Target(
    val id: String
) {
    DESKTOP("desktop"),
    SIMULATOR("simulator"),
    DEVICE("device"),
    ANDROID("android"),
    LINUX("linux"),
    WINDOWS("windows"),
    ;

    /** The iOS targets, driven through Xcode. */
    val isApple: Boolean
        get() = this == SIMULATOR || this == DEVICE

    companion object {
        val choices: Map<String, Target> = mapOf(
            "desktop" to DESKTOP,
            "simulator" to SIMULATOR,
            "sim" to SIMULATOR,
            "device" to DEVICE,
            "android" to ANDROID,
            "linux" to LINUX,
            "windows" to WINDOWS,
        )
    }
}
