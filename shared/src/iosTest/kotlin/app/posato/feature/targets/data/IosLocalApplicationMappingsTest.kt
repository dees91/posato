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
    fun loadRestoresNumberedMappingsAndLiveAccess() = runTest {
        val provider = FakeIosApplicationMappingsProvider(
            loadResponse = response(
                access = IosApplicationMappingsAccess.AUTHORIZATION_DENIED,
                mappings = listOf(IosApplicationMappingReference(identifier(), 3)),
            ),
        )

        val result = assertIs<LocalApplicationMappingsLoadResult.Success>(
            IosLocalApplicationMappings(provider).load(),
        )

        assertEquals(LocalApplicationMappingsAccess.AUTHORIZATION_DENIED, result.access)
        assertEquals(3, assertIs<LocalApplicationMappingDisplay.Numbered>(result.snapshot.mappings.single().display).slot)
    }

    @Test
    fun unavailableRetainsValidMappings() = runTest {
        val provider = FakeIosApplicationMappingsProvider(
            loadResponse = response(
                outcome = IosApplicationMappingsOutcome.UNAVAILABLE,
                access = IosApplicationMappingsAccess.UNAVAILABLE,
                mappings = listOf(IosApplicationMappingReference(identifier(), 1)),
            ),
        )

        val result = assertIs<LocalApplicationMappingsLoadResult.Unavailable>(
            IosLocalApplicationMappings(provider).load(),
        )

        assertEquals(1, result.snapshot.mappings.size)
    }

    @Test
    fun duplicateSlotsAreRejectedAsCorruption() = runTest {
        val provider = FakeIosApplicationMappingsProvider(
            loadResponse = response(
                mappings = listOf(
                    IosApplicationMappingReference(identifier('a'), 2),
                    IosApplicationMappingReference(identifier('b'), 2),
                ),
            ),
        )

        val result = assertIs<LocalApplicationMappingsLoadResult.Failure>(
            IosLocalApplicationMappings(provider).load(),
        )

        assertEquals(LocalApplicationMappingsLoadFailure.CORRUPTION, result.reason)
    }

    @Test
    fun accessChangeRetainsSnapshotAndMapsRestrictedState() = runTest {
        val provider = FakeIosApplicationMappingsProvider(
            selectionResponse = response(
                outcome = IosApplicationMappingsOutcome.ACCESS_CHANGED,
                access = IosApplicationMappingsAccess.RESTRICTED,
                mappings = listOf(IosApplicationMappingReference(identifier(), 4)),
            ),
        )

        val result = assertIs<LocalApplicationSelectionResult.AccessChanged>(
            IosLocalApplicationMappings(provider).chooseApplications(),
        )

        assertEquals(LocalApplicationMappingsAccess.RESTRICTED, result.access)
        assertEquals(4, assertIs<LocalApplicationMappingDisplay.Numbered>(result.snapshot.mappings.single().display).slot)
    }

    @Test
    fun invalidationObservationIsCancelledWithCollector() = runTest {
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
