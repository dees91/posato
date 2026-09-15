package app.posato.buildlogic

import org.gradle.api.GradleException
import java.io.File

object PosatoVersion {
    const val DEVELOPMENT_BUILD_NUMBER = "1"

    private val versionFileContent = Regex("""MARKETING_VERSION = ((?:[1-9][0-9]{0,3})\.(?:0|[1-9][0-9]{0,3})\.(?:0|[1-9][0-9]{0,3}))\n""")
    private val buildNumberValue = Regex("""[1-9][0-9]{0,8}""")

    fun marketingVersion(versionFile: File): String {
        verifyParserContract()
        if (!versionFile.isFile) {
            throw GradleException("Version.xcconfig is missing at the repository root.")
        }
        return parseMarketingVersion(versionFile.readText())
            ?: throw GradleException("Version.xcconfig must contain exactly one line: MARKETING_VERSION = <major>.<minor>.<patch>")
    }

    fun developmentBuildNumber(value: String?): String {
        verifyParserContract()
        if (value == null) {
            return DEVELOPMENT_BUILD_NUMBER
        }
        return parseBuildNumber(value)
            ?: throw GradleException("posatoMacOsBuildNumber must be a positive integer.")
    }

    fun releaseBuildNumber(value: String?): String {
        verifyParserContract()
        return value?.let(::parseBuildNumber)
            ?: throw GradleException("A macOS release needs -PposatoMacOsBuildNumber=<positive integer> above the previous candidate.")
    }

    private fun parseMarketingVersion(content: String): String? {
        return versionFileContent.matchEntire(content)?.groupValues?.get(1)
    }

    private fun parseBuildNumber(value: String): String? {
        return value.takeIf(buildNumberValue::matches)
    }

    private fun verifyParserContract() {
        val acceptedVersions = mapOf(
            "MARKETING_VERSION = 1.0.0\n" to "1.0.0",
            "MARKETING_VERSION = 12.3.45\n" to "12.3.45",
        )
        val rejectedVersions = listOf(
            "MARKETING_VERSION = 1.0.0",
            "MARKETING_VERSION = 1.0\n",
            "MARKETING_VERSION = 0.1.0\n",
            "MARKETING_VERSION = 1.01.0\n",
            "MARKETING_VERSION=1.0.0\n",
            " MARKETING_VERSION = 1.0.0\n",
            "MARKETING_VERSION = 1.0.0\r\n",
            "MARKETING_VERSION = 1.0.0\n\n",
            "MARKETING_VERSION = 1.0.0\nCURRENT_PROJECT_VERSION = 1\n",
            "// comment\nMARKETING_VERSION = 1.0.0\n",
            "MARKETING_VERSION = 1.0.0-beta\n",
        )
        val acceptedBuildNumbers = listOf("1", "42", "999999999")
        val rejectedBuildNumbers = listOf("", "0", "01", "-1", "1.0", " 1", "1000000000")
        val contractFailed = acceptedVersions.any { (content, version) -> parseMarketingVersion(content) != version } ||
            rejectedVersions.any { content -> parseMarketingVersion(content) != null } ||
            acceptedBuildNumbers.any { value -> parseBuildNumber(value) != value } ||
            rejectedBuildNumbers.any { value -> parseBuildNumber(value) != null }
        if (contractFailed) {
            throw GradleException("The Posato version parser failed its regression contract.")
        }
    }
}
