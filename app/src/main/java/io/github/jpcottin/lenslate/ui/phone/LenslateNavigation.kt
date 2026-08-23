package io.github.jpcottin.lenslate.ui.phone

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import io.github.jpcottin.lenslate.ui.phone.home.HomeRoute
import io.github.jpcottin.lenslate.ui.phone.read.ReadRoute
import io.github.jpcottin.lenslate.ui.phone.settings.SettingsRoute
import kotlinx.serialization.Serializable

@Serializable
data object HomeKey : NavKey

@Serializable
data object SettingsKey : NavKey

@Serializable
data object ReadKey : NavKey

/** Navigation 3 back stack for the phone UI: Home ⇄ Settings, Home ⇄ Read. */
@Composable
fun LenslateNavigation() {
    val backStack = rememberNavBackStack(HomeKey)
    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<HomeKey> {
                HomeRoute(
                    onOpenSettings = { if (backStack.lastOrNull() != SettingsKey) backStack.add(SettingsKey) },
                    onOpenRead = { if (backStack.lastOrNull() != ReadKey) backStack.add(ReadKey) },
                )
            }
            entry<SettingsKey> {
                SettingsRoute(onBack = { backStack.removeLastOrNull() })
            }
            entry<ReadKey> {
                ReadRoute(onBack = { backStack.removeLastOrNull() })
            }
        },
    )
}
