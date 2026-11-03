package dev.paperlauncher.launcher

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.store by preferencesDataStore("paper")
private val FAVORITES = stringPreferencesKey("favorites")
private val DOCK = stringPreferencesKey("dock")
private val THEME = stringPreferencesKey("theme")
private const val SEP = "\n"

enum class ThemeMode { PAPER, NIGHT, AUTO }

data class Saved(val favorites: List<String>, val dock: List<String>, val theme: ThemeMode)

private fun String?.ids() = this?.split(SEP)?.filter { it.isNotEmpty() } ?: emptyList()

class Prefs(private val context: Context) {
    val saved: Flow<Saved> = context.store.data.map { p ->
        Saved(
            p[FAVORITES].ids(),
            p[DOCK].ids(),
            runCatching { ThemeMode.valueOf(p[THEME] ?: "AUTO") }.getOrDefault(ThemeMode.AUTO),
        )
    }

    suspend fun setFavorites(ids: List<String>) { context.store.edit { it[FAVORITES] = ids.joinToString(SEP) } }
    suspend fun setDock(ids: List<String>) { context.store.edit { it[DOCK] = ids.joinToString(SEP) } }
    suspend fun setTheme(mode: ThemeMode) { context.store.edit { it[THEME] = mode.name } }
}
