package com.esum.learnfarmore

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.esum.learnfarmore.ui.graph.GraphScreen
import com.esum.learnfarmore.ui.home.HomeScreen
import com.esum.learnfarmore.ui.session.SessionScreen
import com.esum.learnfarmore.ui.settings.SettingsScreen
import com.esum.learnfarmore.ui.theme.LearnHarnessTheme
import kotlinx.serialization.Serializable

@Serializable
internal object HomeRoute

@Serializable
internal data class SessionRoute(val id: String)

@Serializable
internal object GraphRoute

@Serializable
internal object SettingsRoute

@Composable
fun App(container: AppContainer) {
    LearnHarnessTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            var ready by remember { mutableStateOf(false) }
            LaunchedEffect(container) {
                container.load()
                ready = true
            }
            if (ready) {
                AppNavigation(container)
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
private fun AppNavigation(container: AppContainer) {
    val navController = rememberNavController()
    val back: () -> Unit = { navController.popBackStack() }
    NavHost(navController = navController, startDestination = HomeRoute) {
        composable<HomeRoute> {
            HomeScreen(
                container = container,
                onOpenSession = { navController.navigate(SessionRoute(it)) },
                onOpenGraph = { navController.navigate(GraphRoute) },
                onOpenSettings = { navController.navigate(SettingsRoute) },
            )
        }
        composable<SessionRoute> { entry ->
            SessionScreen(
                sessionId = entry.toRoute<SessionRoute>().id,
                container = container,
                onBack = back,
                onOpenSettings = { navController.navigate(SettingsRoute) },
            )
        }
        composable<GraphRoute> {
            GraphScreen(
                container = container,
                onBack = back,
                onOpenSession = { navController.navigate(SessionRoute(it)) },
            )
        }
        composable<SettingsRoute> {
            SettingsScreen(container = container, onBack = back)
        }
    }
}
