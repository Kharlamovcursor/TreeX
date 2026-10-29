package ru.hdlabs.treex

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import ru.hdlabs.treex.update.InstallHelper
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.hdlabs.treex.data.settings.AppSettings
import ru.hdlabs.treex.navigation.TreeNavHost
import ru.hdlabs.treex.ui.theme.HdLabsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as TreeXApplication
        app.container.relativesTreeEngine.attach(this)
        setContent {
            val settings by app.container.settingsRepository.settings.collectAsStateWithLifecycle(AppSettings())
            HdLabsTheme(darkTheme = !settings.lightTheme) {
                TreeNavHost()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        InstallHelper.resumePendingInstall(this)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        (application as TreeXApplication).leaveHint.tryEmit(Unit)
    }
}
