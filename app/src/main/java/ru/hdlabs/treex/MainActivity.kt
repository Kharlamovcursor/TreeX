package ru.hdlabs.treex

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import ru.hdlabs.treex.navigation.TreeNavHost
import ru.hdlabs.treex.ui.theme.HdLabsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HdLabsTheme {
                TreeNavHost()
            }
        }
    }
}
