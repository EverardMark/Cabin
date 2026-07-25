package com.cabin.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircle
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.cabin.app.data.ServiceLocator
import com.cabin.app.ui.auth.AuthScreen
import com.cabin.app.ui.common.FullScreenLoading
import com.cabin.app.ui.create.CreateListingScreen
import com.cabin.app.ui.detail.ListingDetailScreen
import com.cabin.app.ui.listings.ListingsScreen
import com.cabin.app.ui.profile.ProfileScreen

object Routes {
    const val BROWSE = "browse"
    const val POST = "post"
    const val PROFILE = "profile"
    const val DETAIL = "detail/{id}"
    fun detail(id: String) = "detail/$id"
}

@Composable
fun CabinRoot() {
    val repo = ServiceLocator.repository
    var booted by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        repo.bootstrap()
        booted = true
    }

    val user by repo.user.collectAsState()

    when {
        !booted -> FullScreenLoading()
        user == null -> AuthScreen()
        else -> MainScaffold()
    }
}

private data class Tab(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@Composable
private fun MainScaffold() {
    val navController = rememberNavController()
    val tabs = listOf(
        Tab(Routes.BROWSE, "Browse", Icons.Outlined.Search),
        Tab(Routes.POST, "Post", Icons.Outlined.AddCircle),
        Tab(Routes.PROFILE, "Profile", Icons.Outlined.Person),
    )

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in tabs.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                if (currentRoute != tab.route) {
                                    navController.navigate(tab.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.BROWSE,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.BROWSE) {
                ListingsScreen(onOpenListing = { id -> navController.navigate(Routes.detail(id)) })
            }
            composable(Routes.POST) {
                CreateListingScreen(
                    onCreated = { id ->
                        navController.navigate(Routes.detail(id)) {
                            popUpTo(Routes.BROWSE)
                        }
                    },
                )
            }
            composable(Routes.PROFILE) {
                ProfileScreen(onOpenListing = { id -> navController.navigate(Routes.detail(id)) })
            }
            composable(Routes.DETAIL) { entry ->
                ListingDetailScreen(
                    listingId = entry.arguments?.getString("id").orEmpty(),
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
