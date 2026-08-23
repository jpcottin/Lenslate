package io.github.jpcottin.lenslate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.jpcottin.lenslate.ui.phone.LenslateNavigation
import io.github.jpcottin.lenslate.ui.theme.LenslateTheme

/** Phone entry point. The glasses experience lives in [io.github.jpcottin.lenslate.ui.glasses.GlassesActivity]. */
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
