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
    mainClass.set("app.posato.control.MainKt")
    applicationName = "posato-control"
}

dependencies {
    implementation(libs.clikt)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(kotlin("test-junit"))
}

tasks.register<Exec>("swiftFormatCheck") {
    group = "verification"
    description = "Checks formatting for the native accessibility bridge and the iOS driver sources."
    inputs.files(fileTree("native"), fileTree("ios-driver") { include("**/*.swift") })

    commandLine(
        "/usr/bin/xcrun",
        "swift",
        "format",
        "lint",
        "--recursive",
        "--strict",
        "native",
        "ios-driver/PosatoDriverHost",
        "ios-driver/PosatoDriverUITests",
    )
}
