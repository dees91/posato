package app.posato.feature.targets.data

import app.posato.feature.onboarding.ApplicationAccessResult
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class IosLocalApplicationMappingsTest {
    @Test
    fun `given denied live access and an opaque mapping when loaded then both are restored`() = runTest {
        val provider = FakeIosApplicationMappingsProvider(
            loadResponse = response(
                access = IosApplicationMappingsAccess.AUTHORIZATION_DENIED,
                mappings = listOf(IosApplicationMappingReference(identifier())),
            ),
        )

        val result = assertIs<LocalApplicationMappingsLoadResult.Success>(
            IosLocalApplicationMappings(provider).load(),
        )

        assertEquals(LocalApplicationMappingsAccess.AUTHORIZATION_DENIED, result.access)
        assertIs<LocalApplicationMappingDisplay.Opaque>(result.snapshot.mappings.single().display)
    }

    @Test
    fun `given an unavailable provider with a valid mapping when loaded then the mapping is retained`() = runTest {
        val provider = FakeIosApplicationMappingsProvider(
            loadResponse = response(
                outcome = IosApplicationMappingsOutcome.UNAVAILABLE,
                access = IosApplicationMappingsAccess.UNAVAILABLE,
                mappings = listOf(IosApplicationMappingReference(identifier())),
            ),
        )

        val result = assertIs<LocalApplicationMappingsLoadResult.Unavailable>(
            IosLocalApplicationMappings(provider).load(),
        )

        assertEquals(1, result.snapshot.mappings.size)
    }

    @Test
    fun `given duplicate mapping identifiers when loaded then corruption is reported`() = runTest {
        val provider = FakeIosApplicationMappingsProvider(
            loadResponse = response(
                mappings = listOf(
                    IosApplicationMappingReference(identifier('a')),
                    IosApplicationMappingReference(identifier('a')),
                ),
            ),
        )

        val result = assertIs<LocalApplicationMappingsLoadResult.Failure>(
            IosLocalApplicationMappings(provider).load(),
        )

        assertEquals(LocalApplicationMappingsLoadFailure.CORRUPTION, result.reason)
    }

    @Test
    fun `given access changes during selection when completed then the snapshot and restriction are retained`() = runTest {
        val provider = FakeIosApplicationMappingsProvider(
            selectionResponse = response(
                outcome = IosApplicationMappingsOutcome.ACCESS_CHANGED,
                access = IosApplicationMappingsAccess.RESTRICTED,
                mappings = listOf(IosApplicationMappingReference(identifier())),
            ),
        )

        val result = assertIs<LocalApplicationSelectionResult.AccessChanged>(
            IosLocalApplicationMappings(provider).chooseApplications(),
        )

        assertEquals(LocalApplicationMappingsAccess.RESTRICTED, result.access)
        assertIs<LocalApplicationMappingDisplay.Opaque>(result.snapshot.mappings.single().display)
    }

    @Test
    fun `given a rejecting or failing selection outcome when completed then it maps without a snapshot`() = runTest {
        val expectations = mapOf(
            IosApplicationMappingsOutcome.CANCELLED to LocalApplicationSelectionResult.Cancelled,
            IosApplicationMappingsOutcome.CAPACITY to
                LocalApplicationSelectionResult.Rejected(LocalApplicationSelectionRejection.CAPACITY),
            IosApplicationMappingsOutcome.INVALID_SELECTION to
                LocalApplicationSelectionResult.Rejected(LocalApplicationSelectionRejection.UNSUPPORTED),
            IosApplicationMappingsOutcome.PICKER_FAILURE to
                LocalApplicationSelectionResult.Failure(LocalApplicationSelectionFailure.PICKER),
            IosApplicationMappingsOutcome.STORAGE_FAILURE to
                LocalApplicationSelectionResult.Failure(LocalApplicationSelectionFailure.STORAGE),
        )

        expectations.forEach { (outcome, expected) ->
            val provider = FakeIosApplicationMappingsProvider(
                selectionResponse = response(outcome = outcome),
            )

            assertEquals(expected, IosLocalApplicationMappings(provider).chooseApplications(), "outcome $outcome")
        }
    }

    @Test
    fun `given an unavailable build when a selection is requested then the result is unavailable`() = runTest {
        val provider = FakeIosApplicationMappingsProvider(
            selectionResponse = response(
                outcome = IosApplicationMappingsOutcome.UNAVAILABLE,
                access = IosApplicationMappingsAccess.UNAVAILABLE,
            ),
        )

        assertEquals(
            LocalApplicationSelectionResult.Unavailable,
            IosLocalApplicationMappings(provider).chooseApplications(),
        )
    }

    @Test
    fun `given each authorization answer when requested then the read back access is reported`() = runTest {
        val expectations = mapOf(
            IosApplicationMappingsAccess.READY to LocalApplicationMappingsAccess.READY,
            IosApplicationMappingsAccess.AUTHORIZATION_REQUIRED to LocalApplicationMappingsAccess.AUTHORIZATION_REQUIRED,
            IosApplicationMappingsAccess.AUTHORIZATION_DENIED to LocalApplicationMappingsAccess.AUTHORIZATION_DENIED,
            IosApplicationMappingsAccess.RESTRICTED to LocalApplicationMappingsAccess.RESTRICTED,
        )

        expectations.forEach { (answer, expected) ->
            val provider = FakeIosApplicationMappingsProvider(
                authorizationResponse = response(access = answer),
            )

            assertEquals(
                ApplicationAccessResult.Determined(expected),
                IosLocalApplicationMappings(provider).requestAuthorization(),
                "answer $answer",
            )
        }
    }

    @Test
    fun `given an unavailable build when authorization is requested then the result is unavailable`() = runTest {
        val provider = FakeIosApplicationMappingsProvider(
            authorizationResponse = response(
                outcome = IosApplicationMappingsOutcome.UNAVAILABLE,
                access = IosApplicationMappingsAccess.UNAVAILABLE,
            ),
        )

        assertEquals(
            ApplicationAccessResult.Unavailable,
            IosLocalApplicationMappings(provider).requestAuthorization(),
        )
    }

    @Test
    fun `given a failing authorization outcome when requested then failure is reported`() = runTest {
        listOf(
            IosApplicationMappingsOutcome.PICKER_FAILURE,
            IosApplicationMappingsOutcome.STORAGE_FAILURE,
            IosApplicationMappingsOutcome.CORRUPTION,
        ).forEach { outcome ->
            val provider = FakeIosApplicationMappingsProvider(
                authorizationResponse = response(outcome = outcome),
            )

            assertEquals(
                ApplicationAccessResult.Failed,
                IosLocalApplicationMappings(provider).requestAuthorization(),
                "outcome $outcome",
            )
        }
    }

    @Test
    fun `given an invalidation collector when collection completes then native observation is cancelled`() = runTest {
        val provider = FakeIosApplicationMappingsProvider()
        val mappings = IosLocalApplicationMappings(provider)
        val collection = async { mappings.invalidations.first() }
        runCurrent()
        provider.emitInvalidation()

        collection.await()
        assertTrue(provider.observationCancelled)
    }

    private fun response(
        outcome: IosApplicationMappingsOutcome = IosApplicationMappingsOutcome.SUCCESS,
        access: IosApplicationMappingsAccess = IosApplicationMappingsAccess.READY,
        mappings: List<IosApplicationMappingReference> = emptyList(),
    ): IosApplicationMappingsResponse {
        return IosApplicationMappingsResponse(outcome, access, mappings)
    }

    private fun identifier(character: Char = '0'): String {
        return character.toString().repeat(64)
    }
}

private class FakeIosApplicationMappingsProvider(
    private val loadResponse: IosApplicationMappingsResponse = IosApplicationMappingsResponse(
        IosApplicationMappingsOutcome.SUCCESS,
        IosApplicationMappingsAccess.READY,
        emptyList(),
    ),
    private val selectionResponse: IosApplicationMappingsResponse = loadResponse,
    private val authorizationResponse: IosApplicationMappingsResponse = loadResponse,
) : IosApplicationMappingsProvider {
    private var invalidationHandler: (() -> Unit)? = null
    var observationCancelled = false
        private set

    override fun load(completion: (IosApplicationMappingsResponse) -> Unit) {
        completion(loadResponse)
    }

    override fun choose(completion: (IosApplicationMappingsResponse) -> Unit): IosApplicationMappingsOperation {
        completion(selectionResponse)
        return object : IosApplicationMappingsOperation {
            override fun cancel() = Unit
        }
    }

    override fun requestAuthorization(completion: (IosApplicationMappingsResponse) -> Unit): IosApplicationMappingsOperation {
        completion(authorizationResponse)
        return object : IosApplicationMappingsOperation {
            override fun cancel() = Unit
        }
    }

    override fun remove(
        identifier: String,
        completion: (IosApplicationMappingsResponse) -> Unit
    ) {
        completion(loadResponse)
    }

    override fun clear(completion: (IosApplicationMappingsResponse) -> Unit) {
        completion(loadResponse)
    }

    override fun observeInvalidations(handler: () -> Unit): IosApplicationMappingsObservation {
        invalidationHandler = handler
        return object : IosApplicationMappingsObservation {
            override fun cancel() {
                observationCancelled = true
            }
        }
    }

    fun emitInvalidation() {
        invalidationHandler?.invoke()
    }
}
