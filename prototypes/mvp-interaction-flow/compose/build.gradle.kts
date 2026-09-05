import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    androidLibrary {
        namespace = "app.posato.prototype.designsystem"
        compileSdk = libs.versions.androidCompileSdk.get().toInt()
        minSdk = libs.versions.androidMinSdk.get().toInt()
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    jvm {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    iosArm64()
    iosSimulatorArm64()
    jvmToolchain(21)
    compilerOptions {
        allWarningsAsErrors.set(true)
        freeCompilerArgs.add("-opt-in=androidx.compose.material3.ExperimentalMaterial3Api")
    }
    sourceSets {
        commonMain.dependencies {
            api(libs.compose.runtime)
            api(libs.compose.foundation)
            api(libs.compose.ui)
            api(libs.compose.material3)
            implementation(libs.compose.ui.tooling.preview)
        }
        jvmMain.dependencies {
            implementation(libs.compose.desktop.macos.arm64)
        }
    }
}

dependencies {
    add("androidRuntimeClasspath", libs.compose.ui.tooling)
}

compose.desktop {
    application {
        mainClass = "app.posato.prototype.catalog.MainKt"
    }
}

tasks.register("verifyDesignSystem") {
    group = "verification"
    description = "Compiles and checks the prototype-only Compose component library."
    dependsOn("ktlintCheck", "detekt", "compileKotlinJvm", "compileAndroidMain", "compileKotlinIosArm64", "compileKotlinIosSimulatorArm64")
}
