plugins {
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.metro) apply false
}

check(JavaVersion.current() == JavaVersion.VERSION_21) {
    "Posato requires JDK 21; the current Gradle runtime is ${JavaVersion.current()}."
}
