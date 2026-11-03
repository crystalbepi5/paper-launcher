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
        enableEdgeToEdge()
        setContent {
            HomeRoot(
                vm = vm,
                homeRequests = homeRequests.intValue,
                openSettings = { startActivity(Intent(this, SettingsActivity::class.java)) },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refresh()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        homeRequests.intValue += 1
    }
}
