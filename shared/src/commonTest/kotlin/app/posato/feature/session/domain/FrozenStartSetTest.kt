package app.posato.feature.session.domain

import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

class FrozenStartSetTest {
    @Test
    fun `given domains and a count when stored and parsed then the set round trips`() {
        val frozen = FrozenStartSet(persistentListOf("stable.example", "other.example"), 2)

        assertEquals(frozen, FrozenStartSet.parseStored(frozen.toStorageValue(), 2))
    }

    @Test
    fun `given no domains and no count when stored and parsed then the empty set round trips`() {
        val frozen = FrozenStartSet(persistentListOf(), null)

        assertEquals(frozen, FrozenStartSet.parseStored(frozen.toStorageValue(), null))
    }

    @Test
    fun `given two null columns when parsed then there is no frozen set`() {
        assertNull(FrozenStartSet.parseStored(null, null))
    }

    @Test
    fun `given a stored entry outside the domain contract when parsed then parsing fails`() {
        assertFailsWith<IllegalArgumentException> {
            FrozenStartSet.parseStored("ab", null)
        }
        assertFailsWith<IllegalArgumentException> {
            FrozenStartSet.parseStored("stable.example", -1)
        }
    }

    @Test
    fun `given a frozen set when stringified then no domain leaks`() {
        val rendered = FrozenStartSet(persistentListOf("stable.example"), 2).toString()

        assertFalse(rendered.contains("stable.example"))
    }
}
