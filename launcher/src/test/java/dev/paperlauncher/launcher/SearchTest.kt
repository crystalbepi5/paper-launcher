package dev.paperlauncher.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchTest {
    private fun run(q: String, items: List<String>, favs: Set<String> = emptySet()) =
        searchApps(q, items, { it }, { it in favs })

    @Test fun ranksPrefixThenWordThenSubstring() =
        assertEquals(listOf("Maps", "Google Maps", "Bitmap"), run("map", listOf("Bitmap", "Google Maps", "Maps", "Camera")))

    @Test fun favoritesBreakTies() =
        assertEquals(listOf("Mail", "Maps"), run("ma", listOf("Maps", "Mail")) )

    @Test fun favoriteWinsWithinSameScore() =
        assertEquals(listOf("Maps", "Mail"), run("ma", listOf("Mail", "Maps"), setOf("Maps")))

    @Test fun blankQueryReturnsNothing() = assertEquals(emptyList<String>(), run("  ", listOf("Maps")))
}
