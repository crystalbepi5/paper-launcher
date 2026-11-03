@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package dev.paperlauncher.launcher

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val EXPANDED = 700.dp

@Composable
fun PaperTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val night = when (mode) {
        ThemeMode.PAPER -> false
        ThemeMode.NIGHT -> true
        ThemeMode.AUTO -> isSystemInDarkTheme()
    }
    val view = LocalView.current
    val window = (LocalContext.current as? android.app.Activity)?.window
    SideEffect {
        if (window != null) {
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !night
                isAppearanceLightNavigationBars = !night
            }
        }
    }
    CompositionLocalProvider(LocalPalette provides if (night) NightColors else PaperColors, content = content)
}

@Composable
fun HomeRoot(vm: HomeViewModel, homeRequests: Int, openSettings: () -> Unit) {
    val saved by vm.saved.collectAsState()
    val apps by vm.apps.collectAsState()
    val loaded by vm.loaded.collectAsState()
    val notice by vm.notice.collectAsState()
    var screen by rememberSaveable { mutableStateOf("home") }
    var menuId by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(homeRequests) {
        if (homeRequests > 0) { screen = "home"; menuId = null }
    }
    BackHandler(enabled = screen != "home" || menuId != null) {
        if (menuId != null) menuId = null else screen = "home"
    }

    PaperTheme(saved.theme) {
        val c = LocalPalette.current
        Box(Modifier.fillMaxSize().background(c.paper).safeDrawingPadding()) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val expanded = maxWidth >= EXPANDED
                val frost = rememberFrostModifier(expanded, saved.frost, c.paper, c.shade)
                val open: (AppEntry) -> Unit = { vm.launch(it) }
                val menu: (String) -> Unit = { menuId = it }
                Box(Modifier.fillMaxSize().then(frost)) {
                    when (screen) {
                        "apps" -> AppsScreen(vm, apps, loaded, expanded, saved, open, menu) { screen = "home" }
                        "search" -> SearchScreen(vm, apps, saved, expanded, open, menu) { screen = "home" }
                        else -> if (expanded) {
                            ExpandedHome(vm, apps, loaded, saved, open, menu, { screen = "search" }, { screen = "apps" }, openSettings)
                        } else {
                            CompactHome(vm, apps, saved, open, menu, { screen = "search" }, { screen = "apps" }, openSettings)
                        }
                    }
                }
            }
            notice?.let { text ->
                Row(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(c.ink).padding(start = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Label(text, Modifier.weight(1f), color = c.paper)
                    Box(
                        Modifier.size(48.dp).clickable(role = Role.Button, onClick = vm::dismissNotice),
                        contentAlignment = Alignment.Center,
                    ) { Label("OK", color = c.paper, weight = FontWeight.SemiBold) }
                }
            }
            menuId?.let { id ->
                ItemMenu(id, apps.firstOrNull { it.id == id }, saved, vm) { menuId = null }
            }
        }
    }
}

@Composable
private fun Rule(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(LocalPalette.current.ink))
}

@Composable
private fun SearchEntry(placeholder: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = LocalPalette.current
    Row(
        modifier.height(48.dp)
            .border(1.5.dp, c.ink, RoundedCornerShape(10.dp))
            .clickable(role = Role.Button, onClickLabel = "Search", onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Glyph(Glyph.SEARCH, 18.dp, 2f)
        Label(placeholder, color = c.secondary)
    }
}

@Composable
private fun TextAction(text: String, path: String, width: Float, onClick: () -> Unit) {
    Row(
        Modifier.height(48.dp).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Glyph(path, 20.dp, width)
        Label(text)
    }
}

private fun today(): String = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault()))

@Composable
private fun AppIconView(vm: HomeViewModel, entry: AppEntry, size: Dp) {
    val c = LocalPalette.current
    val icon by produceState<LoadedIcon?>(null, entry.id) { value = vm.icon(entry) }
    val loadedIcon = icon
    if (loadedIcon == null) {
        Box(Modifier.size(size).border(1.5.dp, c.line, RoundedCornerShape(8.dp)))
    } else {
        Image(
            loadedIcon.image,
            null,
            Modifier.size(size),
            colorFilter = if (loadedIcon.monochrome) ColorFilter.tint(c.ink) else null,
        )
    }
}

@Composable
private fun AppTile(vm: HomeViewModel, id: String, apps: List<AppEntry>, iconSize: Dp, onOpen: (AppEntry) -> Unit, onMenu: (String) -> Unit, modifier: Modifier = Modifier) {
    val entry = apps.firstOrNull { it.id == id }
    Column(
        modifier.heightIn(min = 76.dp)
            .combinedClickable(
                role = Role.Button,
                onLongClickLabel = "More actions",
                onLongClick = { onMenu(id) },
                onClick = { if (entry != null) onOpen(entry) else onMenu(id) },
            )
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (entry != null) AppIconView(vm, entry, iconSize)
        else Box(Modifier.size(iconSize).border(1.dp, LocalPalette.current.line, RoundedCornerShape(8.dp)))
        Label(entry?.label ?: "Unavailable", size = 13, maxLines = 1)
    }
}

@Composable
private fun TileRows(vm: HomeViewModel, ids: List<String>, apps: List<AppEntry>, columns: Int, iconSize: Dp, onOpen: (AppEntry) -> Unit, onMenu: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ids.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { AppTile(vm, it, apps, iconSize, onOpen, onMenu, Modifier.weight(1f)) }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun EmptyFavorites(onApps: () -> Unit) {
    val c = LocalPalette.current
    Column(
        Modifier.fillMaxWidth().padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Label("No favorites yet", weight = FontWeight.SemiBold)
        Label("Long press any app, or use its menu in All apps.", size = 13, color = c.secondary)
        Box(
            Modifier.height(48.dp).background(c.ink, RoundedCornerShape(10.dp))
                .clickable(role = Role.Button, onClick = onApps).padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center,
        ) { Label("Choose favorites", color = c.paper, weight = FontWeight.SemiBold) }
    }
}

@Composable
private fun CompactHome(vm: HomeViewModel, apps: List<AppEntry>, saved: Saved, onOpen: (AppEntry) -> Unit, onMenu: (String) -> Unit, onSearch: () -> Unit, onApps: () -> Unit, onSettings: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Label(today(), Modifier.weight(1f), size = 16)
            GlyphButton(Glyph.SETTINGS, "Launcher settings", onClick = onSettings)
        }
        Rule()
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val columns = (maxWidth / 104.dp).toInt().coerceAtLeast(3)
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.Center,
            ) {
                if (saved.favorites.isEmpty()) EmptyFavorites(onApps)
                else TileRows(vm, saved.favorites, apps, columns, 34.dp, onOpen, onMenu)
            }
        }
        SearchEntry("Find an app...", Modifier.fillMaxWidth(), onSearch)
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            saved.dock.forEach { AppTile(vm, it, apps, 28.dp, onOpen, onMenu, Modifier.weight(1f)) }
            Column(
                Modifier.weight(1f).heightIn(min = 76.dp).clickable(role = Role.Button, onClickLabel = "All apps", onClick = onApps).padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Glyph(Glyph.GRID, 28.dp, 3f)
                Label("All apps", size = 13)
            }
        }
    }
}

/** Two panes split at the window's center, where the crease measured on this phone sits. */
@Composable
private fun ExpandedHome(vm: HomeViewModel, apps: List<AppEntry>, loaded: Boolean, saved: Saved, onOpen: (AppEntry) -> Unit, onMenu: (String) -> Unit, onSearch: () -> Unit, onApps: () -> Unit, onSettings: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.weight(1f).fillMaxWidth()) {
            Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 24.dp)) {
                Label(today(), Modifier.padding(top = 12.dp, bottom = 12.dp), size = 28, weight = FontWeight.Bold)
                Rule()
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.Center,
                ) {
                    if (saved.favorites.isEmpty()) EmptyFavorites(onApps)
                    else TileRows(vm, saved.favorites, apps, 4, 32.dp, onOpen, onMenu)
                }
                if (saved.dock.isNotEmpty()) {
                    Rule()
                    TileRows(vm, saved.dock, apps, HomeViewModel.DOCK_MAX, 28.dp, onOpen, onMenu)
                }
            }
            Box(Modifier.width(1.dp).fillMaxHeight().background(LocalPalette.current.ink))
            Box(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 24.dp)) {
                AppList(vm, apps, loaded, saved, 1, onOpen, onMenu)
            }
        }
        Rule(Modifier.padding(horizontal = 24.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            SearchEntry("Find an app...", Modifier.weight(1f), onSearch)
            TextAction("All apps", Glyph.GRID, 3f, onApps)
            GlyphButton(Glyph.SETTINGS, "Launcher settings", onClick = onSettings)
        }
    }
}

@Composable
private fun AppRow(vm: HomeViewModel, entry: AppEntry, onOpen: (AppEntry) -> Unit, onMenu: (String) -> Unit) {
    val c = LocalPalette.current
    Column {
        Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).fillMaxHeight().combinedClickable(
                    role = Role.Button,
                    onLongClickLabel = "More actions",
                    onLongClick = { onMenu(entry.id) },
                    onClick = { onOpen(entry) },
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                AppIconView(vm, entry, 26.dp)
                Label(entry.label, size = 16, maxLines = 1)
                if (!entry.isPrimaryProfile) Label("WORK", size = 11, mono = true, color = c.secondary)
            }
            GlyphButton(Glyph.MORE, "More actions for ${entry.label}", 3f) { onMenu(entry.id) }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
    }
}

@Composable
private fun AppList(vm: HomeViewModel, apps: List<AppEntry>, loaded: Boolean, saved: Saved, columns: Int, onOpen: (AppEntry) -> Unit, onMenu: (String) -> Unit) {
    val c = LocalPalette.current
    val groups = remember(apps) { apps.groupBy { it.label.firstOrNull()?.uppercaseChar()?.takeIf(Char::isLetter)?.toString() ?: "#" } }
    if (apps.isEmpty()) {
        Label(if (loaded) "No apps found." else "Loading apps...", Modifier.padding(top = 16.dp), color = c.secondary)
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(48.dp),
    ) {
        groups.forEach { (letter, entries) ->
            item(key = "header-$letter", span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.padding(top = 12.dp)) {
                    Label(letter, size = 12, mono = true, color = c.secondary)
                    Spacer(Modifier.height(4.dp))
                    Rule()
                }
            }
            items(entries, key = { it.id }) { AppRow(vm, it, onOpen, onMenu) }
        }
    }
}

@Composable
private fun Header(title: String, onBack: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        GlyphButton(Glyph.BACK, "Back", 2f, onBack)
        Label(title, Modifier.weight(1f), size = 28, weight = FontWeight.Bold)
        trailing()
    }
}

@Composable
private fun AppsScreen(vm: HomeViewModel, apps: List<AppEntry>, loaded: Boolean, expanded: Boolean, saved: Saved, onOpen: (AppEntry) -> Unit, onMenu: (String) -> Unit, onBack: () -> Unit) {
    val c = LocalPalette.current
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Header("Apps", onBack) { Label("${apps.size} APPS", size = 12, mono = true, color = c.secondary) }
        AppList(vm, apps, loaded, saved, if (expanded) 2 else 1, onOpen, onMenu)
    }
}

@Composable
private fun SearchScreen(vm: HomeViewModel, apps: List<AppEntry>, saved: Saved, expanded: Boolean, onOpen: (AppEntry) -> Unit, onMenu: (String) -> Unit, onBack: () -> Unit) {
    val c = LocalPalette.current
    var query by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val results = remember(query, apps, saved.favorites) {
        searchApps(query, apps, { it.label }, { it.id in saved.favorites })
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp).height(48.dp).border(1.5.dp, c.ink, RoundedCornerShape(10.dp)).padding(start = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Glyph(Glyph.SEARCH, 18.dp, 2f)
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f).focusRequester(focus),
                singleLine = true,
                cursorBrush = SolidColor(c.ink),
                textStyle = TextStyle(fontFamily = Sans, fontSize = 16.sp, color = c.ink),
                decorationBox = { inner ->
                    if (query.isEmpty()) Label("Find an app...", size = 16, color = c.secondary)
                    inner()
                },
            )
            GlyphButton(Glyph.CLOSE, "Close search", 2f, onBack)
        }
        Spacer(Modifier.height(8.dp))
        when {
            query.isBlank() -> Unit
            results.isEmpty() -> Label("No apps match \"$query\".", Modifier.padding(top = 12.dp), color = c.secondary)
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(if (expanded) 2 else 1),
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(48.dp),
            ) {
                items(results, key = { it.id }) { AppRow(vm, it, onOpen, onMenu) }
            }
        }
    }
}

@Composable
private fun ItemMenu(id: String, entry: AppEntry?, saved: Saved, vm: HomeViewModel, onClose: () -> Unit) {
    val c = LocalPalette.current
    Box(Modifier.fillMaxSize().background(c.paper.copy(alpha = 0.85f)).clickable(onClickLabel = "Close menu", onClick = onClose)) {
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(c.paper)
                .clickable(enabled = false) {}
                .padding(horizontal = 24.dp, vertical = 12.dp),
        ) {
            Rule()
            Label(entry?.label ?: "Unavailable app", Modifier.padding(vertical = 12.dp), size = 18, weight = FontWeight.SemiBold)
            if (entry != null) {
                MenuRow("Open") { vm.launch(entry); onClose() }
            }
            MenuRow(if (id in saved.favorites) "Remove from favorites" else "Add to favorites") { vm.toggleFavorite(id); onClose() }
            MenuRow(if (id in saved.dock) "Remove from dock" else "Add to dock") { vm.toggleDock(id); onClose() }
            if (entry != null) {
                MenuRow("App info") { vm.details(entry); onClose() }
            }
            MenuRow("Cancel", onClose)
        }
    }
}

@Composable
private fun MenuRow(text: String, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(48.dp).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.CenterStart,
    ) { Label(text, size = 16, modifier = Modifier.fillMaxWidth(), maxLines = 1) }
}
