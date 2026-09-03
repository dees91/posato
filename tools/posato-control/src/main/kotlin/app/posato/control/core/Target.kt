package app.posato.control.core

enum class Target(
    val id: String
) {
    DESKTOP("desktop"),
    SIMULATOR("simulator"),
    DEVICE("device"),
    ;

    val isApple: Boolean
        get() = this != DESKTOP

    companion object {
        val choices: Map<String, Target> = mapOf(
            "desktop" to DESKTOP,
            "simulator" to SIMULATOR,
            "sim" to SIMULATOR,
            "device" to DEVICE,
        )
    }
}
