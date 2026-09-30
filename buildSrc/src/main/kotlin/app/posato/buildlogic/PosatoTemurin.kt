package app.posato.buildlogic

import org.gradle.api.GradleException
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

object PosatoTemurin {
    const val PINNED_VERSION = "21.0.12.1"
    private const val RELEASE_TAG = "jdk-21.0.12.1%2B1"
    private const val X86_64_ARCHIVE = "OpenJDK21U-jdk_x64_mac_hotspot_21.0.12.1_1.tar.gz"
    private const val X86_64_SHA256 = "44db0f08196daf19a47f90d13388b0c943b67663cb537f998fe29e836fa842ce"

    fun intelHome(
        gradleUserHome: File,
        arm64Home: File,
    ): File {
        val arm64Version = javaVersion(arm64Home)
        if (arm64Version != PINNED_VERSION) {
            throw GradleException(
                "The arm64 Temurin toolchain is $arm64Version, but the x86-64 runtime is pinned to $PINNED_VERSION. " +
                    "Update PosatoTemurin to the same version, archive name, and SHA-256 for both architectures.",
            )
        }
        val root = gradleUserHome.resolve("caches/posato-temurin/$PINNED_VERSION-x86_64")
        val home = root.resolve("jdk/Contents/Home")
        if (home.resolve("release").isFile && javaVersion(home) == PINNED_VERSION) return home
        root.deleteRecursively()
        root.mkdirs()
        val archive = root.resolve(X86_64_ARCHIVE)
        URI("https://github.com/adoptium/temurin21-binaries/releases/download/$RELEASE_TAG/$X86_64_ARCHIVE")
            .toURL()
            .openStream()
            .use { input -> Files.copy(input, archive.toPath(), StandardCopyOption.REPLACE_EXISTING) }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(archive.readBytes())
            .joinToString("") { byte -> "%02x".format(byte) }
        if (digest != X86_64_SHA256) {
            root.deleteRecursively()
            throw GradleException("The downloaded $X86_64_ARCHIVE does not match its pinned SHA-256.")
        }
        val extracted = root.resolve("jdk")
        extracted.mkdirs()
        val process = ProcessBuilder("/usr/bin/tar", "-xzf", archive.absolutePath, "-C", extracted.absolutePath, "--strip-components", "1")
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        if (process.waitFor() != 0) {
            root.deleteRecursively()
            throw GradleException("Extracting $X86_64_ARCHIVE failed: $output")
        }
        archive.delete()
        if (javaVersion(home) != PINNED_VERSION) {
            throw GradleException("The extracted x86-64 runtime does not report JAVA_VERSION $PINNED_VERSION.")
        }
        return home
    }

    private fun javaVersion(home: File): String? {
        val release = home.resolve("release")
        if (!release.isFile) return null
        return release.readLines()
            .firstOrNull { line -> line.startsWith("JAVA_VERSION=") }
            ?.substringAfter('=')
            ?.trim('"')
    }
}
