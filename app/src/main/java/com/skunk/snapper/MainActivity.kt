package com.skunk.snapper

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.skunk.snapper.ui.SnapperApp
import com.skunk.snapper.ui.theme.SnapperTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SnapperTheme {
                SnapperApp()
            }
        }
    }
}
