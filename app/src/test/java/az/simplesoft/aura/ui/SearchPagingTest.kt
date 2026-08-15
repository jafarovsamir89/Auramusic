package az.simplesoft.aura.ui

import az.simplesoft.aura.data.providers.MusicSearchRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchPagingTest {
    @Test
    fun searchStartsAboveLegacyTenResultLimitAndLoadsUntilProviderMaximum() {
        assertEquals(20, SearchPaging.INITIAL_LIMIT)
        assertEquals(SearchPaging.INITIAL_LIMIT, MusicSearchRequest("test").limit)
        assertEquals(40, SearchPaging.nextLimit(SearchPaging.INITIAL_LIMIT))
        assertEquals(50, SearchPaging.nextLimit(40))
        assertNull(SearchPaging.nextLimit(SearchPaging.MAX_LIMIT))
    }
}
