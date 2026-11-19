package me.paliyo.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchTest {
    @Test fun ranking() {
        assertEquals(0, matchRank("Maps", "ma"))
        assertEquals(1, matchRank("Google Maps", "maps"))
        assertEquals(2, matchRank("Instagram", "gram"))
        assertEquals(0, matchRank("Café Bar", "cafe"))
        assertEquals(null, matchRank("Signal", "xyz"))
    }
}
