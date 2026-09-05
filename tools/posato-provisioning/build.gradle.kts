import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

kotlin {
    jvmToolchain(21)

    compilerOptions {
        allWarningsAsErrors.set(true)
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

application {
    mainClass.set("app.posato.provisioning.MainKt")
    applicationName = "posato-provisioning"
}

dependencies {
    implementation(libs.clikt)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(kotlin("test-junit"))
}
