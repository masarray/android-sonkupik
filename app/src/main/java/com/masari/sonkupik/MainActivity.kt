package com.masari.sonkupik

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.masari.sonkupik.ui.player.SonkuPikPlayerScreen
import com.masari.sonkupik.ui.theme.SonkupikTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            SonkupikTheme {
                SonkuPikPlayerScreen()
            }
        }
    }
}