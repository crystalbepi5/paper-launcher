package dev.paperlauncher.launcher

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.mutableIntStateOf

class HomeActivity : ComponentActivity() {
    private val vm: HomeViewModel by viewModels()
    private val homeRequests = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FrostTrace.add("activity created, restored=${savedInstanceState != null}")
        enableEdgeToEdge()
        setContent {
            HomeRoot(
                vm = vm,
                homeRequests = homeRequests.intValue,
                openSettings = { startActivity(Intent(this, SettingsActivity::class.java)) },
            )
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        FrostTrace.add("config ${newConfig.screenWidthDp}x${newConfig.screenHeightDp}")
    }

    override fun onStop() {
        FrostTrace.add("activity stopped")
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        FrostTrace.add("activity resumed")
        vm.refresh()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        homeRequests.intValue += 1
    }
}
