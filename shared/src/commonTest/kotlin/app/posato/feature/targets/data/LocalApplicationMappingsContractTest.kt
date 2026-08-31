package app.posato.feature.targets.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocalApplicationMappingsContractTest {
    @Test
    fun `identity and mapping reject noncanonical or sensitive values`() {
        val id = assertNotNull(LocalApplicationMappingId.restore("ab".repeat(32)))

        assertNull(LocalApplicationMappingId.restore("AB".repeat(32)))
        assertNull(LocalApplicationMapping.restore(id, " Browser "))
        assertNull(LocalApplicationMapping.restore(id, "Browser\nApp"))
        assertEquals("LocalApplicationMappingId(redacted)", id.toString())
        assertEquals("LocalApplicationMapping(redacted)", assertNotNull(LocalApplicationMapping.restore(id, "Browser")).toString())
    }

    @Test
    fun `snapshot sorts mappings and rejects duplicate identities`() {
        val firstId = assertNotNull(LocalApplicationMappingId.restore("01".repeat(32)))
        val secondId = assertNotNull(LocalApplicationMappingId.restore("02".repeat(32)))
        val first = assertNotNull(LocalApplicationMapping.restore(firstId, "Zulu"))
        val second = assertNotNull(LocalApplicationMapping.restore(secondId, "alpha"))

        val snapshot = assertNotNull(LocalApplicationMappingsSnapshot.restore(listOf(first, second)))

        assertEquals(listOf("alpha", "Zulu"), snapshot.mappings.map { mapping -> mapping.displayName })
        assertNull(LocalApplicationMappingsSnapshot.restore(listOf(first, first)))
        assertTrue(snapshot.toString().contains("redacted"))
    }
}
