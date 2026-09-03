package app.posato.feature.targets.data

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
