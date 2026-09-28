package app.posato.provisioning.store

import app.posato.provisioning.asc.HttpMethod
import app.posato.provisioning.core.ErrorCode
import app.posato.provisioning.core.ProvisioningException
import app.posato.provisioning.store.StoreFixtures.APPS
import app.posato.provisioning.store.StoreFixtures.ATTACHED_BUILD
import app.posato.provisioning.store.StoreFixtures.NO_BUILD
import app.posato.provisioning.store.StoreFixtures.NO_VERSIONS
import app.posato.provisioning.store.StoreFixtures.list
import app.posato.provisioning.store.StoreFixtures.versionEntry
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class StorePreparationTest {
    private val request = PrepareRequest("1.2.0", 5, "New things.", ReleaseType.MANUAL, screenshots = null)
    private val renaming = PrepareRequest("1.2.0", 5, "New things.", ReleaseType.AFTER_APPROVAL, screenshots = null, renameFrom = "1.1.0")

    @Test
    fun `creates the version, attaches the build, and sets What's New on a fresh release`() {
        val harness = StoreHarness(
            mapOf(
                "GET apps" to listOf(APPS),
                "GET builds" to listOf(StoreFixtures.builds()),
                "GET apps/APP/appStoreVersions" to listOf(NO_VERSIONS),
                "POST appStoreVersions" to listOf(StoreFixtures.CREATED_VERSION),
                "GET appStoreVersions/VER/build" to listOf(NO_BUILD),
                "PATCH appStoreVersions/VER/relationships/build" to listOf(""),
                "GET appStoreVersions/VER/appStoreVersionLocalizations" to listOf(StoreFixtures.localizations("Old things.")),
                "PATCH appStoreVersionLocalizations/LOC" to listOf("""{"data":{"id":"LOC"}}"""),
            ),
        )

        val result = StorePreparation(harness.services).prepare(request)

        assertEquals("created", result.text("appStoreVersion"))
        assertEquals("attached", result.text("build"))
        assertEquals("updated", result.text("whatsNew"))
        val builds = harness.executor.requests.first { it.path == "builds" }
        assertTrue(builds.query.containsAll(listOf("filter[app]" to "APP", "filter[version]" to "5", "include" to "preReleaseVersion")))

        val create = harness.executor.write("POST appStoreVersions").data()
        assertEquals("appStoreVersions", create.text("type"))
        val attributes = create["attributes"]!!.jsonObject
        assertEquals("IOS", attributes.text("platform"))
        assertEquals("1.2.0", attributes.text("versionString"))
        assertEquals("MANUAL", attributes.text("releaseType"))
        assertEquals("APP", create["relationships"]!!.jsonObject["app"]!!.jsonObject["data"]!!.jsonObject.text("id"))

        val attach = harness.executor.write("PATCH appStoreVersions/VER/relationships/build").data()
        assertEquals("builds", attach.text("type"))
        assertEquals("B5", attach.text("id"))

        val localization = harness.executor.write("PATCH appStoreVersionLocalizations/LOC").data()
        assertEquals("LOC", localization.text("id"))
        assertEquals("New things.", localization["attributes"]!!.jsonObject.text("whatsNew"))
    }

    @Test
    fun `issues only reads when the version already matches`() {
        val harness = StoreHarness(
            mapOf(
                "GET apps" to listOf(APPS),
                "GET builds" to listOf(StoreFixtures.builds()),
                "GET apps/APP/appStoreVersions" to listOf(StoreFixtures.version(releaseType = "MANUAL")),
                "GET appStoreVersions/VER/build" to listOf(ATTACHED_BUILD),
                "GET appStoreVersions/VER/appStoreVersionLocalizations" to listOf(StoreFixtures.localizations("New things.")),
            ),
        )

        val result = StorePreparation(harness.services).prepare(request)

        assertTrue(harness.executor.writes.isEmpty())
        assertEquals("unchanged", result.text("appStoreVersion"))
        assertEquals("unchanged", result.text("releaseType"))
        assertEquals("unchanged", result.text("build"))
        assertEquals("unchanged", result.text("whatsNew"))
    }

    @Test
    fun `sets a new description with What's New in one write and leaves a matching one alone`() {
        val routes = { description: String ->
            mapOf(
                "GET apps" to listOf(APPS),
                "GET builds" to listOf(StoreFixtures.builds()),
                "GET apps/APP/appStoreVersions" to listOf(StoreFixtures.version(releaseType = "MANUAL")),
                "GET appStoreVersions/VER/build" to listOf(ATTACHED_BUILD),
                "GET appStoreVersions/VER/appStoreVersionLocalizations" to listOf(StoreFixtures.localizations("Old things.", description)),
                "PATCH appStoreVersionLocalizations/LOC" to listOf("""{"data":{"id":"LOC"}}"""),
            )
        }
        val described = PrepareRequest("1.2.0", 5, "New things.", ReleaseType.MANUAL, screenshots = null, description = "Schedules too.")

        val changed = StoreHarness(routes("Old description."))
        val result = StorePreparation(changed.services).prepare(described)

        assertEquals("updated", result.text("description"))
        assertEquals("updated", result.text("whatsNew"))
        assertEquals(1, changed.executor.writes.size)
        val attributes = changed.executor.write("PATCH appStoreVersionLocalizations/LOC").data()["attributes"]!!.jsonObject
        assertEquals("Schedules too.", attributes.text("description"))
        assertEquals("New things.", attributes.text("whatsNew"))

        val matching = StoreHarness(routes("Schedules too."))
        val kept = StorePreparation(matching.services).prepare(described)

        assertEquals("unchanged", kept.text("description"))
        val onlyWhatsNew = matching.executor.write("PATCH appStoreVersionLocalizations/LOC").data()["attributes"]!!.jsonObject
        assertEquals(setOf("whatsNew"), onlyWhatsNew.keys)
    }

    @Test
    fun `changes only the release type of an existing version`() {
        val harness = StoreHarness(
            mapOf(
                "GET apps" to listOf(APPS),
                "GET builds" to listOf(StoreFixtures.builds()),
                "GET apps/APP/appStoreVersions" to listOf(StoreFixtures.version(releaseType = "AFTER_APPROVAL")),
                "PATCH appStoreVersions/VER" to listOf("""{"data":{"id":"VER"}}"""),
                "GET appStoreVersions/VER/build" to listOf(ATTACHED_BUILD),
                "GET appStoreVersions/VER/appStoreVersionLocalizations" to listOf(StoreFixtures.localizations("New things.")),
            ),
        )

        StorePreparation(harness.services).prepare(request)

        val patch = harness.executor.writes.single()
        assertEquals(HttpMethod.PATCH, patch.method)
        assertEquals("MANUAL", patch.data()["attributes"]!!.jsonObject.text("releaseType"))
        assertEquals("VER", patch.data().text("id"))
    }

    @Test
    fun `refuses a version that is waiting for review before its first write`() {
        val harness = StoreHarness(
            mapOf(
                "GET apps" to listOf(APPS),
                "GET builds" to listOf(StoreFixtures.builds()),
                "GET apps/APP/appStoreVersions" to listOf(StoreFixtures.version(releaseType = "AFTER_APPROVAL", state = "WAITING_FOR_REVIEW")),
            ),
        )

        val failure = assertFailsWith<ProvisioningException> { StorePreparation(harness.services).prepare(request) }

        assertEquals(ErrorCode.VERSION_NOT_EDITABLE, failure.code)
        assertTrue(failure.message.orEmpty().contains("WAITING_FOR_REVIEW"))
        assertTrue(harness.executor.writes.isEmpty())
    }

    @Test
    fun `changes a rejected version, which App Store Connect lets a developer edit`() {
        val harness = StoreHarness(
            mapOf(
                "GET apps" to listOf(APPS),
                "GET builds" to listOf(StoreFixtures.builds()),
                "GET apps/APP/appStoreVersions" to listOf(StoreFixtures.version(releaseType = "MANUAL", state = "METADATA_REJECTED")),
                "GET appStoreVersions/VER/build" to listOf(ATTACHED_BUILD),
                "GET appStoreVersions/VER/appStoreVersionLocalizations" to listOf(StoreFixtures.localizations("Old things.")),
                "PATCH appStoreVersionLocalizations/LOC" to listOf("""{"data":{"id":"LOC"}}"""),
            ),
        )

        val result = StorePreparation(harness.services).prepare(request)

        assertEquals("updated", result.text("whatsNew"))
        assertEquals(listOf("PATCH appStoreVersionLocalizations/LOC"), harness.executor.writes.map { "${it.method} ${it.path}" })
    }

    @Test
    fun `refuses a build that is still processing and writes nothing`() {
        val harness = StoreHarness(
            mapOf("GET apps" to listOf(APPS), "GET builds" to listOf(StoreFixtures.builds(state = "PROCESSING"))),
        )

        val failure = assertFailsWith<ProvisioningException> { StorePreparation(harness.services).prepare(request) }

        assertEquals(ErrorCode.BUILD_NOT_READY, failure.code)
        assertTrue(failure.message.orEmpty().contains("PROCESSING"))
        assertTrue(harness.executor.writes.isEmpty())
    }

    @Test
    fun `refuses a build number that belongs to another marketing version`() {
        val harness = StoreHarness(
            mapOf("GET apps" to listOf(APPS), "GET builds" to listOf(StoreFixtures.builds(marketing = "1.1.0"))),
        )

        val failure = assertFailsWith<ProvisioningException> { StorePreparation(harness.services).prepare(request) }

        assertEquals(ErrorCode.BUILD_MISSING, failure.code)
        assertTrue(harness.executor.writes.isEmpty())
    }

    @Test
    fun `selects the app by its exact bundle identifier`() {
        val harness = StoreHarness(
            mapOf("GET apps" to listOf("""{"data":[{"id":"EXT","attributes":{"bundleId":"app.posato.ios.activitymonitor"}}]}""")),
        )

        val failure = assertFailsWith<ProvisioningException> { StorePreparation(harness.services).prepare(request) }

        assertEquals(ErrorCode.APP_MISSING, failure.code)
        assertTrue(harness.executor.requests.single().query.contains("filter[bundleId]" to "app.posato.ios"))
    }

    @Test
    fun `renames the one unreleased version and prepares it in the same run`() {
        val harness = StoreHarness(
            mapOf(
                "GET apps" to listOf(APPS),
                "GET builds" to listOf(StoreFixtures.builds()),
                "GET apps/APP/appStoreVersions" to listOf(
                    list(versionEntry("LIVE", "1.0.0", "READY_FOR_DISTRIBUTION"), versionEntry("VER", "1.1.0", "DEVELOPER_REJECTED")),
                ),
                "PATCH appStoreVersions/VER" to listOf("""{"data":{"id":"VER","attributes":{"versionString":"1.2.0"}}}"""),
                "GET appStoreVersions/VER/build" to listOf(ATTACHED_BUILD.replace("B5", "B4")),
                "PATCH appStoreVersions/VER/relationships/build" to listOf(""),
                "GET appStoreVersions/VER/appStoreVersionLocalizations" to listOf(StoreFixtures.localizations("Old things.")),
                "PATCH appStoreVersionLocalizations/LOC" to listOf("""{"data":{"id":"LOC"}}"""),
            ),
        )

        val result = StorePreparation(harness.services).prepare(renaming)

        assertEquals("renamed", result.text("appStoreVersion"))
        assertEquals("unchanged", result.text("releaseType"))
        assertEquals("attached", result.text("build"))
        assertEquals(
            listOf("PATCH appStoreVersions/VER", "PATCH appStoreVersions/VER/relationships/build", "PATCH appStoreVersionLocalizations/LOC"),
            harness.executor.writes.map { "${it.method} ${it.path}" },
        )
        val rename = harness.executor.write("PATCH appStoreVersions/VER").data()
        assertEquals("appStoreVersions", rename.text("type"))
        assertEquals("VER", rename.text("id"))
        val attributes = rename["attributes"]!!.jsonObject
        assertEquals(setOf("versionString"), attributes.keys)
        assertEquals("1.2.0", attributes.text("versionString"))
    }

    @Test
    fun `issues only reads when a rename already happened`() {
        val harness = StoreHarness(
            mapOf(
                "GET apps" to listOf(APPS),
                "GET builds" to listOf(StoreFixtures.builds()),
                "GET apps/APP/appStoreVersions" to listOf(
                    list(versionEntry("LIVE", "1.0.0", "READY_FOR_DISTRIBUTION"), versionEntry("VER", "1.2.0", "DEVELOPER_REJECTED")),
                ),
                "GET appStoreVersions/VER/build" to listOf(ATTACHED_BUILD),
                "GET appStoreVersions/VER/appStoreVersionLocalizations" to listOf(StoreFixtures.localizations("New things.")),
            ),
        )

        val result = StorePreparation(harness.services).prepare(renaming)

        assertTrue(harness.executor.writes.isEmpty())
        assertEquals("unchanged", result.text("appStoreVersion"))
    }

    @Test
    fun `refuses to rename a version that is still waiting for review, with no write`() {
        val failure = refusedRename(versionEntry("LIVE", "1.0.0", "READY_FOR_DISTRIBUTION"), versionEntry("VER", "1.1.0", "WAITING_FOR_REVIEW"))

        assertEquals(ErrorCode.VERSION_NOT_EDITABLE, failure.code)
        assertTrue(failure.message.orEmpty().contains("WAITING_FOR_REVIEW"))
        assertTrue(failure.hint.orEmpty().contains("store withdraw --version 1.1.0"))
    }

    @Test
    fun `refuses to rename while another version is unreleased or in an unknown state, with no write`() {
        listOf("PENDING_DEVELOPER_RELEASE", "A_STATE_ADDED_LATER").forEach { state ->
            val failure = refusedRename(versionEntry("OTHER", "1.0.1", state), versionEntry("VER", "1.1.0", "PREPARE_FOR_SUBMISSION"))

            assertEquals(ErrorCode.VERSION_NOT_EDITABLE, failure.code)
            assertTrue(failure.message.orEmpty().contains("1.0.1 is $state"))
        }
    }

    @Test
    fun `refuses to rename a version App Store Connect does not hold, with no write`() {
        val failure = refusedRename(versionEntry("LIVE", "1.0.0", "READY_FOR_DISTRIBUTION"))

        assertEquals(ErrorCode.VERSION_MISSING, failure.code)
        assertTrue(failure.message.orEmpty().contains("1.1.0"))
    }

    /** Runs a rename of 1.1.0 against an account holding [versions] and asserts it failed before any write. */
    private fun refusedRename(vararg versions: String): ProvisioningException {
        val harness = StoreHarness(
            mapOf(
                "GET apps" to listOf(APPS),
                "GET builds" to listOf(StoreFixtures.builds()),
                "GET apps/APP/appStoreVersions" to listOf(list(*versions)),
            ),
        )
        val failure = assertFailsWith<ProvisioningException> { StorePreparation(harness.services).prepare(renaming) }
        assertTrue(harness.executor.writes.isEmpty())
        return failure
    }
}
