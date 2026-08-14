package com.openconvert.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.openconvert.app.ui.OpenConvertApp
import com.openconvert.app.ui.theme.OpenConvertTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OpenConvertTheme {
                OpenConvertApp()
            }
        }
    }
}

