package com.bbolbbol.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.bbolbbol.app.ui.nav.AppNav
import com.bbolbbol.app.ui.theme.BbolTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as BbolApplication).container
        setContent {
            BbolTheme {
                Surface(Modifier.fillMaxSize()) { AppNav(container) }
            }
        }
    }
}
