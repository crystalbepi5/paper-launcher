package dev.paperlauncher.launcher

import java.util.Locale

/** Local only. Exact prefix, then word prefix, then substring. Favorites break ties. */
fun <T> searchApps(query: String, items: List<T>, label: (T) -> String, isFavorite: (T) -> Boolean): List<T> {
    val q = query.trim().lowercase(Locale.ROOT)
    if (q.isEmpty()) return emptyList()
    return items.mapNotNull { item ->
        val name = label(item).lowercase(Locale.ROOT)
        val score = when {
            name.startsWith(q) -> 0
            name.split(' ', '-', '_', '.').any { it.startsWith(q) } -> 1
            name.contains(q) -> 2
            else -> return@mapNotNull null
        }
        Triple(item, score, name)
    }.sortedWith(compareBy({ it.second }, { !isFavorite(it.first) }, { it.third })).map { it.first }
}
