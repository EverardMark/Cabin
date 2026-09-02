package com.cabin.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircle
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
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
import com.cabin.app.ui.map.MapSearchScreen
import com.cabin.app.ui.messages.ChatScreen
import com.cabin.app.ui.messages.ChatViewModel
import com.cabin.app.ui.messages.MessagesScreen
import com.cabin.app.ui.profile.ProfileScreen
import com.cabin.app.ui.searches.SavedSearchesScreen
import com.cabin.app.ui.userprofile.UserProfileScreen
import com.cabin.app.ui.userprofile.UserProfileViewModel
import com.cabin.app.ui.viewings.ViewingsScreen

object Routes {
    const val BROWSE = "browse"
    const val MESSAGES = "messages"
    const val POST = "post"
    const val VIEWINGS = "viewings"
    const val PROFILE = "profile"
    const val SEARCHES = "searches"
    const val MAP = "map"
    const val DETAIL = "detail/{id}"
    const val CHAT = "chat/{id}"
    const val USER = "user/{id}"

    fun detail(id: String) = "detail/$id"
    fun chat(id: String) = "chat/$id"
    fun user(id: String) = "user/$id"
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

private data class Tab(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val badge: Int = 0,
)

@Composable
private fun MainScaffold() {
    val navController = rememberNavController()
    val summary by ServiceLocator.repository.summary.collectAsState()

    // Posting is open to everyone: owners are the largest group of posters in
    // the survey, and no respondent wanted an agents-only marketplace.
    val tabs = listOf(
        Tab(Routes.BROWSE, "Browse", Icons.Outlined.Search),
        Tab(Routes.MESSAGES, "Messages", Icons.Outlined.ChatBubbleOutline, summary.unreadMessages),
        Tab(Routes.POST, "Post", Icons.Outlined.AddCircle),
        Tab(Routes.VIEWINGS, "Viewings", Icons.Outlined.CalendarMonth, summary.pendingViewingRequests),
        Tab(Routes.PROFILE, "Profile", Icons.Outlined.Person),
    )

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in tabs.map { it.route }

    LaunchedEffect(Unit) { ServiceLocator.repository.refreshSummary() }

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
                            icon = {
                                BadgedBox(badge = {
                                    if (tab.badge > 0) Badge { Text(tab.badge.toString()) }
                                }) {
                                    Icon(tab.icon, contentDescription = tab.label)
                                }
                            },
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
                ListingsScreen(
                    onOpenListing = { id -> navController.navigate(Routes.detail(id)) },
                    onOpenMap = { navController.navigate(Routes.MAP) },
                )
            }
            composable(Routes.MAP) {
                MapSearchScreen(onOpenListing = { id -> navController.navigate(Routes.detail(id)) })
            }
            composable(Routes.MESSAGES) {
                MessagesScreen(onOpenConversation = { id -> navController.navigate(Routes.chat(id)) })
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
            composable(Routes.VIEWINGS) {
                ViewingsScreen(onOpenListing = { id -> navController.navigate(Routes.detail(id)) })
            }
            composable(Routes.PROFILE) {
                ProfileScreen(
                    onOpenListing = { id -> navController.navigate(Routes.detail(id)) },
                    onOpenSavedSearches = { navController.navigate(Routes.SEARCHES) },
                    onOpenViewings = { navController.navigate(Routes.VIEWINGS) },
                )
            }
            composable(Routes.SEARCHES) {
                SavedSearchesScreen(onOpenListing = { id -> navController.navigate(Routes.detail(id)) })
            }
            composable(Routes.DETAIL) { entry ->
                ListingDetailScreen(
                    listingId = entry.arguments?.getString("id").orEmpty(),
                    onBack = { navController.popBackStack() },
                    onOpenConversation = { id -> navController.navigate(Routes.chat(id)) },
                    onOpenProfile = { id -> navController.navigate(Routes.user(id)) },
                )
            }
            composable(Routes.CHAT) { entry ->
                val id = entry.arguments?.getString("id").orEmpty()
                ChatScreen(
                    conversationId = id,
                    onOpenListing = { listingId -> navController.navigate(Routes.detail(listingId)) },
                    viewModel = viewModel(factory = factoryFor { ChatViewModel(id) }),
                )
            }
            composable(Routes.USER) { entry ->
                val id = entry.arguments?.getString("id").orEmpty()
                UserProfileScreen(
                    viewModel = viewModel(factory = factoryFor { UserProfileViewModel(id) }),
                )
            }
        }
    }
}

/** Builds a one-off ViewModel factory for view models that take an id. */
private fun <VM : ViewModel> factoryFor(create: () -> VM): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = create() as T
    }
