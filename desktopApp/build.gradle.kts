import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
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
    implementation(project(":shared"))
    implementation(libs.compose.desktop.macos.arm64)
}

compose.desktop {
    application {
        mainClass = "app.posato.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg)
            packageName = "Posato"
            packageVersion = "1.0.0"
            modules("java.sql")

            macOS {
                bundleID = "app.posato.macos"
                minimumSystemVersion = "15.0"
            }
        }
    }
}
