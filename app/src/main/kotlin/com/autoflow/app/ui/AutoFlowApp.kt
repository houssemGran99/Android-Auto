package com.autoflow.app.ui

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.autoflow.app.ui.automations.AutomationListScreen
import com.autoflow.app.ui.builder.BuilderScreen
import com.autoflow.app.ui.history.ExecutionDetailScreen
import com.autoflow.app.ui.history.HistoryScreen
import com.autoflow.app.ui.home.HomeScreen
import com.autoflow.app.ui.search.SearchScreen
import com.autoflow.app.ui.settings.PermissionsScreen
import com.autoflow.app.ui.settings.SettingsScreen
import com.autoflow.app.ui.templates.TemplatesScreen
import com.autoflow.app.ui.variables.VariablesScreen

/** App shell: bottom navigation on phones, navigation rail on wide screens. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AutoFlowApp() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val topLevel = TopLevelDestination.entries.firstOrNull { it.route == currentRoute }
    val wide = LocalConfiguration.current.screenWidthDp >= WIDE_SCREEN_DP

    val navigateTopLevel: (TopLevelDestination) -> Unit = { destination ->
        navController.navigate(destination.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        // Only navigation-bar insets here; each screen's own Scaffold handles the status bar.
        contentWindowInsets = WindowInsets.navigationBars,
        bottomBar = {
            if (!wide && topLevel != null) {
                NavigationBar {
                    TopLevelDestination.entries.forEach { destination ->
                        NavigationBarItem(
                            selected = destination == topLevel,
                            onClick = { navigateTopLevel(destination) },
                            icon = { Icon(destination.icon, contentDescription = null) },
                            label = { Text(stringResource(destination.label)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Row(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding),
        ) {
            if (wide && topLevel != null) {
                NavigationRail {
                    TopLevelDestination.entries.forEach { destination ->
                        NavigationRailItem(
                            selected = destination == topLevel,
                            onClick = { navigateTopLevel(destination) },
                            icon = { Icon(destination.icon, contentDescription = null) },
                            label = { Text(stringResource(destination.label)) },
                        )
                    }
                }
            }
            AppNavHost(navController, Modifier.weight(1f))
        }
    }
}

@Composable
private fun AppNavHost(navController: NavHostController, modifier: Modifier) {
    val back: () -> Unit = { navController.popBackStack() }
    NavHost(navController, startDestination = TopLevelDestination.HOME.route, modifier = modifier) {
        composable(TopLevelDestination.HOME.route) {
            HomeScreen(
                onOpenAutomation = { navController.navigate(Routes.editAutomation(it)) },
                onOpenExecution = { navController.navigate(Routes.execution(it)) },
                onCreate = { navController.navigate(Routes.newAutomation()) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenSearch = { navController.navigate(Routes.SEARCH) },
                onOpenHistory = { navController.navigate(TopLevelDestination.HISTORY.route) },
            )
        }
        composable(TopLevelDestination.AUTOMATIONS.route) {
            AutomationListScreen(
                onOpenAutomation = { navController.navigate(Routes.editAutomation(it)) },
                onCreate = { navController.navigate(Routes.newAutomation()) },
                onOpenTemplates = { navController.navigate(TopLevelDestination.TEMPLATES.route) },
            )
        }
        composable(TopLevelDestination.TEMPLATES.route) {
            TemplatesScreen(onUseTemplate = { navController.navigate(Routes.fromTemplate(it)) })
        }
        composable(TopLevelDestination.HISTORY.route) {
            HistoryScreen(onOpenExecution = { navController.navigate(Routes.execution(it)) })
        }
        composable(TopLevelDestination.VARIABLES.route) {
            VariablesScreen()
        }
        composable(
            Routes.BUILDER,
            arguments = listOf(
                navArgument(Routes.ARG_AUTOMATION_ID) { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument(Routes.ARG_TEMPLATE_ID) { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
        ) {
            BuilderScreen(
                onBack = back,
                onOpenExecution = { navController.navigate(Routes.execution(it)) },
            )
        }
        composable(
            Routes.EXECUTION,
            arguments = listOf(navArgument(Routes.ARG_EXECUTION_ID) { type = NavType.StringType }),
        ) {
            ExecutionDetailScreen(
                onBack = back,
                onOpenAutomation = { navController.navigate(Routes.editAutomation(it)) },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = back, onOpenPermissions = { navController.navigate(Routes.PERMISSIONS) })
        }
        composable(Routes.PERMISSIONS) {
            PermissionsScreen(onBack = back)
        }
        composable(Routes.SEARCH) {
            SearchScreen(
                onBack = back,
                onOpenAutomation = { navController.navigate(Routes.editAutomation(it)) },
                onOpenTemplate = { navController.navigate(Routes.fromTemplate(it)) },
                onOpenExecution = { navController.navigate(Routes.execution(it)) },
                onOpenVariables = { navController.navigate(TopLevelDestination.VARIABLES.route) },
            )
        }
    }
}

private const val WIDE_SCREEN_DP = 600
