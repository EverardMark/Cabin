package com.cabin.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.cabin.app.data.ServiceLocator
import com.cabin.app.ui.auth.AuthFlowScreen
import com.cabin.app.ui.common.FullScreenLoading
import com.cabin.app.ui.common.SoftBackground
import com.cabin.app.ui.common.softClick
import com.cabin.app.ui.common.softShadowTab
import com.cabin.app.ui.create.CreateListingScreen
import com.cabin.app.ui.detail.ListingDetailScreen
import com.cabin.app.ui.detail.ListingDetailViewModel
import com.cabin.app.ui.listings.ListingsScreen
import com.cabin.app.ui.map.MapSearchScreen
import com.cabin.app.ui.messages.ChatScreen
import com.cabin.app.ui.messages.ChatViewModel
import com.cabin.app.ui.messages.MessagesScreen
import com.cabin.app.ui.profile.ProfileScreen
import com.cabin.app.ui.searches.SavedSearchesScreen
import com.cabin.app.ui.theme.SoftAccent
import com.cabin.app.ui.theme.SoftInk
import com.cabin.app.ui.theme.SoftTextSoft
import com.cabin.app.ui.userprofile.UserProfileScreen
import com.cabin.app.ui.userprofile.UserProfileViewModel
import com.cabin.app.ui.viewings.ViewingsScreen

object Routes {
    const val BROWSE = "browse"
    const val MESSAGES = "messages"
    const val MAP = "map"
    const val VIEWINGS = "viewings"
    const val PROFILE = "profile"
    const val POST = "post"
    const val SEARCHES = "searches"
    const val DETAIL = "detail/{id}"
    const val CHAT = "chat/{id}"
    const val USER = "user/{id}"
    const val EDIT = "edit/{id}"

    fun detail(id: String) = "detail/$id"
    fun chat(id: String) = "chat/$id"
    fun user(id: String) = "user/$id"
    fun edit(id: String) = "edit/$id"
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
    val onboarding by repo.onboarding.collectAsState()

    SoftBackground {
        when {
            !booted -> FullScreenLoading()
            user == null || onboarding -> AuthFlowScreen()
            else -> MainScaffold()
        }
    }
}

/** The five tabs of the floating pill bar (the design has no Post tab; posting lives in Profile). */
private data class SoftTab(val route: String, val icon: ImageVector, val label: String)

private val tabs = listOf(
    SoftTab(Routes.BROWSE, Icons.Outlined.Home, "Browse"),
    SoftTab(Routes.MESSAGES, Icons.Outlined.ChatBubbleOutline, "Messages"),
    SoftTab(Routes.MAP, Icons.Outlined.Place, "Map"),
    SoftTab(Routes.VIEWINGS, Icons.Outlined.Description, "Viewings"),
    SoftTab(Routes.PROFILE, Icons.Outlined.Person, "Profile"),
)

@Composable
private fun MainScaffold() {
    val navController = rememberNavController()
    val summary by ServiceLocator.repository.summary.collectAsState()

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in tabs.map { it.route }

    LaunchedEffect(Unit) { ServiceLocator.repository.refreshSummary() }

    fun switchTab(route: String) {
        if (currentRoute == route) return
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        CabinNavHost(navController, onSwitchTab = ::switchTab)

        if (showBottomBar) {
            SoftTabBar(
                selected = currentRoute,
                badges = mapOf(
                    Routes.MESSAGES to summary.unreadMessages,
                    Routes.VIEWINGS to summary.pendingViewingRequests,
                ),
                onSelect = ::switchTab,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 8.dp),
            )
        }
    }
}

/** The floating white pill with five circular tabs (.tab). */
@Composable
private fun SoftTabBar(
    selected: String?,
    badges: Map<String, Int>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .width(334.dp)
            .height(68.dp)
            .softShadowTab(CircleShape)
            .clip(CircleShape)
            .background(Color.White)
            .padding(horizontal = 18.dp),
    ) {
        tabs.forEach { tab ->
            val on = tab.route == selected
            Box(modifier = Modifier.size(54.dp)) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(if (on) SoftInk else Color.Transparent)
                        .softClick { onSelect(tab.route) },
                ) {
                    Icon(
                        tab.icon,
                        contentDescription = tab.label,
                        tint = if (on) Color.White else SoftTextSoft,
                        modifier = Modifier.size(24.dp),
                    )
                }
                if ((badges[tab.route] ?: 0) > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = (-12).dp, y = 12.dp)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(SoftAccent),
                    )
                }
            }
        }
    }
}

@Composable
private fun CabinNavHost(navController: NavHostController, onSwitchTab: (String) -> Unit) {
    NavHost(
        navController = navController,
        startDestination = Routes.BROWSE,
        modifier = Modifier.fillMaxSize(),
    ) {
        composable(Routes.BROWSE) {
            ListingsScreen(
                onOpenListing = { id -> navController.navigate(Routes.detail(id)) },
                onOpenConversation = { id -> navController.navigate(Routes.chat(id)) },
                onOpenMessages = { onSwitchTab(Routes.MESSAGES) },
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
                    navController.navigate(Routes.detail(id)) { popUpTo(Routes.PROFILE) }
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
                onOpenPost = { navController.navigate(Routes.POST) },
            )
        }
        composable(Routes.SEARCHES) {
            SavedSearchesScreen(
                onOpenListing = { id -> navController.navigate(Routes.detail(id)) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.DETAIL) { entry ->
            ListingDetailScreen(
                listingId = entry.arguments?.getString("id").orEmpty(),
                onBack = { navController.popBackStack() },
                onOpenConversation = { id -> navController.navigate(Routes.chat(id)) },
                onOpenProfile = { id -> navController.navigate(Routes.user(id)) },
                onEdit = { id -> navController.navigate(Routes.edit(id)) },
            )
        }
        composable(Routes.CHAT) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            ChatScreen(
                conversationId = id,
                onBack = { navController.popBackStack() },
                onOpenListing = { listingId -> navController.navigate(Routes.detail(listingId)) },
                viewModel = viewModel(factory = factoryFor { ChatViewModel(id) }),
            )
        }
        composable(Routes.EDIT) { entry ->
            EditListingRoute(
                listingId = entry.arguments?.getString("id").orEmpty(),
                onSaved = { navController.popBackStack() },
                onDeleted = {
                    // The listing is gone, so don't return to its detail screen.
                    navController.popBackStack(Routes.BROWSE, inclusive = false)
                },
            )
        }
        composable(Routes.USER) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            UserProfileScreen(
                onBack = { navController.popBackStack() },
                viewModel = viewModel(factory = factoryFor { UserProfileViewModel(id) }),
            )
        }
    }
}

/**
 * Loads a listing, then hands it to the shared form in edit mode. Editing is the
 * repair path for a flagged listing, so it has to work from a bare id.
 */
@Composable
private fun EditListingRoute(listingId: String, onSaved: () -> Unit, onDeleted: () -> Unit) {
    val loader: ListingDetailViewModel = viewModel(key = "edit-$listingId")
    LaunchedEffect(listingId) { loader.load(listingId) }
    val state by loader.state.collectAsStateWithLifecycle()

    val listing = state.listing
    if (listing == null) {
        FullScreenLoading()
    } else {
        CreateListingScreen(
            onCreated = { onSaved() },
            editing = listing,
            onDeleted = onDeleted,
        )
    }
}

/** Builds a one-off ViewModel factory for view models that take an id. */
private fun <VM : ViewModel> factoryFor(create: () -> VM): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = create() as T
    }
