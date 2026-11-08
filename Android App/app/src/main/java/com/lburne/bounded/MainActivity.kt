package com.lburne.bounded

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.lburne.bounded.ui.BoundedApp
import com.lburne.bounded.ui.theme.RiftboundTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val app = application as BoundedApplication
        setContent {
            RiftboundTheme {
                BoundedApp(app)
            }
        }
    }
}
