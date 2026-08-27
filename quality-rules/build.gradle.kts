import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
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

dependencies {
    implementation(libs.ktlint.cli.ruleset.core)
    implementation(libs.ktlint.rule.engine.core)

    testImplementation(kotlin("test-junit"))
    testImplementation(libs.ktlint.rule.engine)
    testRuntimeOnly(libs.slf4j.simple)
}
