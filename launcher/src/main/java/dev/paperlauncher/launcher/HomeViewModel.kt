package dev.paperlauncher.launcher

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = Prefs(app)
    private val catalog = AppCatalog(app) { refresh() }

    var lastExpanded: Boolean? = null
    val apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val loaded = MutableStateFlow(false)
    val notice = MutableStateFlow<String?>(null)
    val saved: StateFlow<Saved> = prefs.saved.stateIn(
        viewModelScope, SharingStarted.Eagerly, Saved(emptyList(), emptyList(), ThemeMode.AUTO),
    )

    init { catalog.start(); refresh() }

    override fun onCleared() { catalog.stop() }

    fun refresh() {
        viewModelScope.launch {
            apps.value = withContext(Dispatchers.Default) { catalog.load() }
            loaded.value = true
        }
    }

    suspend fun icon(entry: AppEntry): LoadedIcon? = withContext(Dispatchers.Default) {
        runCatching { catalog.icon(entry) }.getOrNull()
    }

    fun launch(entry: AppEntry) {
        notice.value = when (catalog.launch(entry)) {
            LaunchResult.Ok -> null
            LaunchResult.Missing -> "${entry.label} is no longer available."
            LaunchResult.Paused -> "${entry.label} is in a paused profile."
            LaunchResult.Denied -> "Android didn't allow ${entry.label} to open."
        }
    }

    fun details(entry: AppEntry) = catalog.openDetails(entry)
    fun dismissNotice() { notice.value = null }

    fun toggleFavorite(id: String) = viewModelScope.launch {
        val now = prefs.saved.first().favorites
        prefs.setFavorites(if (id in now) now - id else now + id)
    }

    fun toggleDock(id: String) = viewModelScope.launch {
        val now = prefs.saved.first().dock
        when {
            id in now -> prefs.setDock(now - id)
            now.size < DOCK_MAX -> prefs.setDock(now + id)
            else -> notice.value = "The dock holds $DOCK_MAX apps. Remove one first."
        }
    }

    companion object { const val DOCK_MAX = 4 }
}
