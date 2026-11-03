package dev.paperlauncher.launcher

import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** Launchable on its own, so there is always a way back if the home screen misbehaves. */
class SettingsActivity : ComponentActivity() {
    private val resumes = mutableIntStateOf(0)

    override fun onResume() { super.onResume(); resumes.intValue += 1 }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = Prefs(applicationContext)
        val roles = getSystemService(RoleManager::class.java)
        setContent {
            val saved by prefs.saved.collectAsState(Saved(emptyList(), emptyList(), ThemeMode.AUTO))
            val scope = rememberCoroutineScope()
            val tick = resumes.intValue
            val isHome = remember(tick) { roles.isRoleHeld(RoleManager.ROLE_HOME) }
            val available = remember { roles.isRoleAvailable(RoleManager.ROLE_HOME) }
            val request = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { resumes.intValue += 1 }
            PaperTheme(saved.theme) {
                val c = LocalPalette.current
                Box(Modifier.fillMaxSize().background(c.paper).safeDrawingPadding()) {
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Label("Launcher settings", size = 28, weight = FontWeight.Bold)
                        Label("DEFAULT HOME", size = 12, mono = true, color = c.secondary)
                        Label(if (isHome) "Paper is your home screen." else "Preview. Paper is not your home screen.", size = 16)
                        if (!isHome && available) {
                            ActionButton("Set Paper as home", primary = true) {
                                request.launch(roles.createRequestRoleIntent(RoleManager.ROLE_HOME))
                            }
                        }
                        ActionButton("Open default home settings", primary = false) {
                            try {
                                startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
                            } catch (_: ActivityNotFoundException) {
                                startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
                            }
                        }
                        Label(
                            "To go back, choose One UI Home there. One UI Home stays installed. If Paper ever fails to open, this settings app has its own icon, or use Settings > Apps > Default apps > Home app.",
                            size = 14,
                            color = c.secondary,
                        )
                        Label("EFFECTS", Modifier.padding(top = 12.dp), size = 12, mono = true, color = c.secondary)
                        ActionButton(if (saved.frost) "Reduce effects: off" else "Reduce effects: on", primary = false) {
                            scope.launch { prefs.setFrost(!saved.frost) }
                        }
                        Label(
                            "Frost plays when you unfold with Paper on screen. It also stays off in Battery Saver and when system animations are off.",
                            size = 14,
                            color = c.secondary,
                        )
                        Label("FROST TRACE", Modifier.padding(top = 12.dp), size = 12, mono = true, color = c.secondary)
                        Label(remember(tick) { FrostTrace.text().ifEmpty { "Nothing yet." } }, size = 11, mono = true)
                        Label("THEME", Modifier.padding(top = 12.dp), size = 12, mono = true, color = c.secondary)
                        Row(Modifier.fillMaxWidth().border(1.5.dp, c.ink, RoundedCornerShape(10.dp))) {
                            ThemeMode.entries.forEach { mode ->
                                val on = mode == saved.theme
                                Box(
                                    Modifier.weight(1f).height(48.dp)
                                        .background(if (on) c.ink else c.paper, RoundedCornerShape(8.dp))
                                        .clickable(role = Role.Button) { scope.launch { prefs.setTheme(mode) } },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Label(
                                        when (mode) { ThemeMode.PAPER -> "Paper"; ThemeMode.NIGHT -> "Night ink"; ThemeMode.AUTO -> "Auto" },
                                        color = if (on) c.paper else c.ink,
                                        weight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionButton(text: String, primary: Boolean, onClick: () -> Unit) {
    val c = LocalPalette.current
    Box(
        Modifier.fillMaxWidth().height(48.dp)
            .background(if (primary) c.ink else c.paper, RoundedCornerShape(10.dp))
            .border(1.5.dp, c.ink, RoundedCornerShape(10.dp))
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Label(text, color = if (primary) c.paper else c.ink, weight = FontWeight.SemiBold) }
}
