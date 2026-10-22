package app.posato.roadmap

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Items are found by their title prefix `<ID>: `. A row ID is a prefix of a longer one (`SYNC-02` of `SYNC-021`), and
 * editing the wrong public item would misreport another row's state, which nobody notices until the board is read.
 */
class RowLookupTest {
    private val items = listOf(
        ProjectItem("PVTI_1", "DI_1", "SYNC-021: Finish a long removal with one press"),
        ProjectItem("PVTI_2", "DI_2", "SYNC-022: Rebuild the long-history fixture"),
    )

    @Test
    fun `given a row ID when finding its item then only the exact ID before the colon matches`() {
        assertEquals("PVTI_2", findRow(items, "SYNC-022").id)
        assertFailsWith<MirrorException> { findRow(items, "SYNC-02") }
    }

    @Test
    fun `given two items for one row when finding it then it refuses instead of picking one`() {
        assertFailsWith<MirrorException> { findRow(items + ProjectItem("PVTI_3", "DI_3", "SYNC-022: Duplicate"), "SYNC-022") }
    }
}
