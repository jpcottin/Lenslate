package com.jpcottin.lenslate.ui.phone

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation3.SupportingPaneSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberSupportingPaneSceneStrategy
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.jpcottin.lenslate.ui.phone.home.HomeRoute
import com.jpcottin.lenslate.ui.phone.read.ReadRoute
import com.jpcottin.lenslate.ui.phone.settings.SettingsRoute
import kotlinx.serialization.Serializable

@Serializable
data object HomeKey : NavKey

@Serializable
data object SettingsKey : NavKey

@Serializable
data object ReadKey : NavKey

/**
 * Navigation 3 back stack for the phone UI. On phones each key is a full screen; on tablets,
 * unfolded foldables and desktop windows the supporting-pane scene shows Settings next to Home.
 * Read has no pane metadata on purpose: the viewfinder stays full screen everywhere.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun LenslateNavigation() {
    val backStack = rememberNavBackStack(HomeKey)

    // Pops must be idempotent: a double-tapped back arrow during the exit animation, or an
    // async pop (Read finishing) racing a manual one, must never empty the back stack —
    // NavDisplay throws on an empty stack.
    fun popIfTop(key: NavKey) {
        if (backStack.size > 1 && backStack.lastOrNull() == key) backStack.removeLastOrNull()
    }
    // Never stack panes vertically: on a tall compact window (folded cover display) Settings
    // must take the whole screen, not share it with Home.
    val paneDirective = calculatePaneScaffoldDirective(currentWindowAdaptiveInfo())
        .copy(maxVerticalPartitions = 1)
    val supportingPaneStrategy = rememberSupportingPaneSceneStrategy<NavKey>(directive = paneDirective)
    NavDisplay(
        backStack = backStack,
        onBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
        // Specify decorators explicitly to include the ViewModelStore one: without it every
        // route viewModel{} lands in the Activity store and is never cleared on pop.
        // (NavDisplay adds its internal scene-setup decorator on its own.)
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        sceneStrategies = listOf(supportingPaneStrategy),
        entryProvider = entryProvider {
            entry<HomeKey>(metadata = SupportingPaneSceneStrategy.mainPane()) {
                HomeRoute(
                    onOpenSettings = { if (backStack.lastOrNull() != SettingsKey) backStack.add(SettingsKey) },
                    onOpenRead = { if (backStack.lastOrNull() != ReadKey) backStack.add(ReadKey) },
                )
            }
            entry<SettingsKey>(metadata = SupportingPaneSceneStrategy.supportingPane()) {
                SettingsRoute(onBack = { popIfTop(SettingsKey) })
            }
            entry<ReadKey> {
                ReadRoute(onBack = { popIfTop(ReadKey) })
            }
        },
    )
}
