import org.gradle.api.initialization.resolve.RepositoriesMode

pluginManagement {
    repositories {
        google {
            content {
                includeGroup("com.android")
                includeGroupByRegex("com\\.android\\..*")
                includeGroupByRegex("androidx\\..*")
            }
        }
        gradlePluginPortal()
        mavenCentral()
    }
    // The R8 bundled with the Android Gradle plugin cannot read Kotlin 2.4 metadata; the release build pins a
    // newer one (https://developer.android.com/build/kotlin-support).
    buildscript {
        repositories {
            google {
                content { includeGroup("com.android.tools") }
            }
        }
        dependencies {
            classpath("com.android.tools:r8:9.5.29")
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroup("com.android")
                includeGroupByRegex("androidx\\..*")
                includeGroupByRegex("com\\.android\\..*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "Posato"

include(":androidApp")
include(":desktopApp")
include(":linuxHelper")
include(":macosHelper")
include(":macosSyncCompanion")
include(":posato-control")
include(":posato-provisioning")
include(":prototypeApp")
include(":prototypeDesignSystem")
include(":quality-rules")
include(":roadmap-mirror")
include(":shared")
include(":windowsHelper")

project(":posato-control").projectDir = file("tools/posato-control")
project(":posato-provisioning").projectDir = file("tools/posato-provisioning")
project(":roadmap-mirror").projectDir = file("tools/roadmap-mirror")
project(":prototypeApp").projectDir = file("prototypes/mvp-interaction-flow/app")
project(":prototypeDesignSystem").projectDir = file("prototypes/mvp-interaction-flow/compose")
