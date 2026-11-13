package com.bbolbbol.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.bbolbbol.app.ui.nav.AppNav
import com.bbolbbol.app.ui.theme.BbolTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 앱은 밝은 화면만 쓴다. 시스템이 어두운 모드여도 상태바/내비게이션바 아이콘은 어둡게(light 스타일) 그린다.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        val container = (application as BbolApplication).container
        setContent {
            BbolTheme {
                Surface(Modifier.fillMaxSize()) { AppNav(container) }
            }
        }
    }
}
