package com.jpcottin.lenslate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.jpcottin.lenslate.ui.phone.LenslateNavigation
import com.jpcottin.lenslate.ui.theme.LenslateTheme

/** Phone entry point. The glasses experience lives in [com.jpcottin.lenslate.ui.glasses.GlassesActivity]. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LenslateTheme {
                LenslateNavigation()
            }
        }
    }
}
