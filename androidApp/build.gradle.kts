plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

val posatoVersionName = "1.6.1"
val twoDigits = 0..99

// The build digit of one upload, `-Pposato.android.build=<0..99>`. Play never accepts a versionCode twice, so a
// re-upload of the same versionName takes the next digit; local builds default to 0.
val posatoBuildDigit: Int = providers.gradleProperty("posato.android.build").orNull.let { raw ->
    val digit = if (raw == null) 0 else raw.trim().toIntOrNull()
    if (digit == null || digit !in twoDigits) {
        throw GradleException("posato.android.build must be a whole number from 0 to 99, got '$raw'.")
    }
    digit
}

// versionCode = major * 1000000 + minor * 10000 + patch * 100 + build, so 1.6.1 build 1 is 1060101.
val posatoVersionCode: Int = posatoVersionName.split('.').mapNotNull { it.toIntOrNull() }.let { parts ->
    if (parts.size != 3 || parts[1] !in twoDigits || parts[2] !in twoDigits) {
        throw GradleException("versionName '$posatoVersionName' must be major.minor.patch with minor and patch up to 99.")
    }
    parts[0] * 1_000_000 + parts[1] * 10_000 + parts[2] * 100 + posatoBuildDigit
}

android {
    namespace = "app.posato.android"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()

    defaultConfig {
        applicationId = "app.posato.android"
        minSdk = libs.versions.androidMinSdk.get().toInt()
        targetSdk = libs.versions.androidCompileSdk.get().toInt()
        versionCode = posatoVersionCode
        versionName = posatoVersionName
    }

    // The release APK (GitHub Releases) and AAB (Google Play) leave Gradle unsigned; releasing.md signs them.
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources.excludes += setOf("META-INF/versions/9/OSGI-INF/MANIFEST.MF", "META-INF/AL2.0", "META-INF/LGPL2.1")
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    implementation(libs.sqldelight.android.driver)
    implementation(libs.sqldelight.async.extensions)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(kotlin("test-junit"))
}
