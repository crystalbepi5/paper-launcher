package dev.paperlauncher.launcher

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import java.text.Collator

data class AppEntry(
    val id: String,
    val label: String,
    val component: ComponentName,
    val user: UserHandle,
    val isPrimaryProfile: Boolean,
    val paused: Boolean,
)

class LoadedIcon(val image: ImageBitmap, val monochrome: Boolean, val bytes: Int)

sealed interface LaunchResult {
    data object Ok : LaunchResult
    data object Missing : LaunchResult
    data object Paused : LaunchResult
    data object Denied : LaunchResult
}

/** All Android package access lives here. Identity is component plus profile serial, never package alone. */
class AppCatalog(context: Context, private val onChanged: () -> Unit) {
    private val apps = context.getSystemService(LauncherApps::class.java)
    private val users = context.getSystemService(UserManager::class.java)
    private val infos = HashMap<String, LauncherActivityInfo>()
    private val icons = object : LruCache<String, LoadedIcon>(32 * 1024 * 1024) {
        override fun sizeOf(key: String, value: LoadedIcon) = value.bytes
    }

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) = changed()
        override fun onPackageAdded(packageName: String, user: UserHandle) = changed()
        override fun onPackageChanged(packageName: String, user: UserHandle) = changed()
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = changed()
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = changed()
    }

    private fun changed() { icons.evictAll(); onChanged() }

    fun start() = apps.registerCallback(callback, Handler(Looper.getMainLooper()))
    fun stop() = apps.unregisterCallback(callback)

    @Synchronized
    fun load(): List<AppEntry> {
        val collator = Collator.getInstance()
        val found = HashMap<String, LauncherActivityInfo>()
        val entries = users.userProfiles.flatMap { user ->
            val serial = users.getSerialNumberForUser(user)
            val paused = users.isQuietModeEnabled(user)
            apps.getActivityList(null, user).map { info ->
                val id = "${info.componentName.flattenToString()}|$serial"
                found[id] = info
                AppEntry(id, info.label.toString(), info.componentName, user, user == Process.myUserHandle(), paused)
            }
        }
        infos.clear()
        infos.putAll(found)
        return entries.sortedWith { a, b -> collator.compare(a.label, b.label) }
    }

    fun launch(entry: AppEntry): LaunchResult {
        if (entry.paused) return LaunchResult.Paused
        return try {
            apps.startMainActivity(entry.component, entry.user, null, null)
            LaunchResult.Ok
        } catch (e: ActivityNotFoundException) {
            LaunchResult.Missing
        } catch (e: SecurityException) {
            LaunchResult.Denied
        } catch (e: IllegalArgumentException) {
            LaunchResult.Missing
        }
    }

    fun openDetails(entry: AppEntry) {
        try { apps.startAppDetailsActivity(entry.component, entry.user, null, null) } catch (_: RuntimeException) { }
    }

    /** Monochrome when the app provides one, original colors otherwise. No tracing or filters. */
    @Synchronized
    fun icon(entry: AppEntry): LoadedIcon? {
        icons.get(entry.id)?.let { return it }
        val info = infos[entry.id] ?: return null
        val px = 144
        val base = info.getIcon(0)
        val mono = (base as? AdaptiveIconDrawable)?.monochrome
        val bitmap: Bitmap = if (mono != null && entry.isPrimaryProfile) {
            val out = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            mono.setBounds(-px / 4, -px / 4, px + px / 4, px + px / 4)
            mono.setTint(Color.WHITE)
            mono.draw(Canvas(out))
            out
        } else {
            (if (entry.isPrimaryProfile) base else info.getBadgedIcon(0)).toBitmap(px, px, Bitmap.Config.ARGB_8888)
        }
        val loaded = LoadedIcon(bitmap.asImageBitmap(), mono != null && entry.isPrimaryProfile, bitmap.byteCount)
        icons.put(entry.id, loaded)
        return loaded
    }
}
