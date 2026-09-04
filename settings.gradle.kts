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

include(":desktopApp")
include(":macosHelper")
include(":macosSyncCompanion")
include(":posato-control")
include(":quality-rules")
include(":shared")

project(":posato-control").projectDir = file("tools/posato-control")
