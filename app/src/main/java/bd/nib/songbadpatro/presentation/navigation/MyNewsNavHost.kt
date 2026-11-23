package bd.nib.songbadpatro.presentation.navigation

import android.net.Uri
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PeopleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import bd.nib.songbadpatro.domain.model.AppLanguage
import bd.nib.songbadpatro.presentation.screens.authors.AuthorProfileScreen
import bd.nib.songbadpatro.presentation.screens.authors.AuthorProfileViewModel
import bd.nib.songbadpatro.presentation.screens.authors.AuthorsScreen
import bd.nib.songbadpatro.presentation.screens.authors.AuthorsViewModel
import bd.nib.songbadpatro.presentation.screens.categories.CategoriesScreen
import bd.nib.songbadpatro.presentation.screens.categories.CategoriesViewModel
import bd.nib.songbadpatro.presentation.screens.home.HomeScreen
import bd.nib.songbadpatro.presentation.screens.home.HomeViewModel
import bd.nib.songbadpatro.presentation.screens.reader.ReaderScreen
import bd.nib.songbadpatro.presentation.screens.reader.ReaderViewModel
import bd.nib.songbadpatro.presentation.screens.saved.SavedScreen
import bd.nib.songbadpatro.presentation.screens.saved.SavedViewModel
import bd.nib.songbadpatro.presentation.screens.search.SearchScreen
import bd.nib.songbadpatro.presentation.screens.search.SearchViewModel
import bd.nib.songbadpatro.presentation.screens.settings.AdminMaintenanceScreen
import bd.nib.songbadpatro.presentation.screens.settings.AdminMaintenanceViewModel
import bd.nib.songbadpatro.presentation.screens.settings.PolicyAndAboutScreen
import bd.nib.songbadpatro.presentation.screens.settings.PolicyScreenType
import bd.nib.songbadpatro.presentation.screens.settings.SettingsScreen
import bd.nib.songbadpatro.presentation.screens.settings.SettingsViewModel
import bd.nib.songbadpatro.presentation.screens.sources.SourcesScreen
import bd.nib.songbadpatro.presentation.screens.sources.SourcesViewModel

object NavRoutes {
    const val HOME = "home"
    const val CATEGORIES = "categories"
    const val AUTHORS = "authors"
    const val SAVED = "saved"
    const val SEARCH = "search"
    const val READER = "reader/{articleId}"
    const val AUTHOR_PROFILE = "author/{authorId}"
    const val SOURCES = "sources"
    const val SETTINGS = "settings"
    const val MAINTENANCE = "maintenance"
    const val PRIVACY = "policy/privacy"
    const val COLLECTION_POLICY = "policy/collection"
    const val ABOUT = "policy/about"

    fun reader(articleId: String) = "reader/${Uri.encode(articleId)}"
    fun authorProfile(authorId: String) = "author/${Uri.encode(authorId)}"
}

private data class BottomDestination(
    val route: String,
    val titleBn: String,
    val titleEn: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val mainDestinations = listOf(
    BottomDestination(NavRoutes.HOME, "হোম", "Home", Icons.Filled.Home, Icons.Outlined.Home),
    BottomDestination(NavRoutes.CATEGORIES, "বিভাগ", "Categories", Icons.Filled.Category, Icons.Outlined.Category),
    BottomDestination(NavRoutes.AUTHORS, "লেখক", "Authors", Icons.Filled.People, Icons.Outlined.PeopleOutline),
    BottomDestination(NavRoutes.SAVED, "সংরক্ষিত", "Saved", Icons.Filled.Bookmark, Icons.Outlined.BookmarkBorder)
)

@Composable
fun MyNewsAppShell(
    language: AppLanguage,
    deepLinkArticleId: String? = null,
    navController: NavHostController = rememberNavController()
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val showMainNav = currentDestination?.route in mainDestinations.map { it.route }

    LaunchedEffect(deepLinkArticleId) {
        if (!deepLinkArticleId.isNullOrBlank()) {
            navController.navigate(NavRoutes.reader(deepLinkArticleId))
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isTabletExpanded = maxWidth >= 600.dp

        Scaffold(
            bottomBar = {
                if (showMainNav && !isTabletExpanded) {
                    NavigationBar(modifier = Modifier.testTag("bottom_nav_bar")) {
                        mainDestinations.forEach { dest ->
                            val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
                            val label = if (language == AppLanguage.ENGLISH) dest.titleEn else dest.titleBn
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(dest.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = label
                                    )
                                },
                                label = { Text(label) },
                                modifier = Modifier.testTag("nav_item_${dest.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (showMainNav && isTabletExpanded) {
                    NavigationRail(modifier = Modifier.testTag("tablet_nav_rail")) {
                        mainDestinations.forEach { dest ->
                            val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
                            val label = if (language == AppLanguage.ENGLISH) dest.titleEn else dest.titleBn
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(dest.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = label
                                    )
                                },
                                label = { Text(label) }
                            )
                        }
                    }
                }

                MyNewsNavGraph(
                    navController = navController,
                    language = language,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun MyNewsNavGraph(
    navController: NavHostController,
    language: AppLanguage,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = NavRoutes.HOME,
        modifier = modifier
    ) {
        composable(NavRoutes.HOME) {
            val vm: HomeViewModel = hiltViewModel()
            val state by vm.uiState.collectAsStateWithLifecycle()
            HomeScreen(
                state = state,
                onRefresh = vm::refreshFeed,
                onOpenArticle = { id -> navController.navigate(NavRoutes.reader(id)) },
                onToggleBookmark = vm::toggleBookmark,
                onOpenCategory = { navController.navigate(NavRoutes.CATEGORIES) },
                onOpenAuthor = { id -> navController.navigate(NavRoutes.authorProfile(id)) },
                onOpenSearch = { navController.navigate(NavRoutes.SEARCH) },
                onOpenSources = { navController.navigate(NavRoutes.SOURCES) },
                onOpenSettings = { navController.navigate(NavRoutes.SETTINGS) },
                onOpenAdminQueue = { navController.navigate(NavRoutes.MAINTENANCE) }
            )
        }

        composable(NavRoutes.CATEGORIES) {
            val vm: CategoriesViewModel = hiltViewModel()
            val state by vm.uiState.collectAsStateWithLifecycle()
            CategoriesScreen(
                state = state,
                onSelectCategory = vm::selectCategory,
                onSelectLanguage = vm::selectLanguage,
                onSelectPublisher = vm::selectPublisher,
                onSelectSort = vm::selectSort,
                onOpenArticle = { id -> navController.navigate(NavRoutes.reader(id)) },
                onOpenAuthor = { id -> navController.navigate(NavRoutes.authorProfile(id)) },
                onToggleBookmark = vm::toggleBookmark
            )
        }

        composable(NavRoutes.AUTHORS) {
            val vm: AuthorsViewModel = hiltViewModel()
            val state by vm.uiState.collectAsStateWithLifecycle()
            AuthorsScreen(
                state = state,
                onSelectRole = vm::selectRole,
                onUpdateQuery = vm::updateSearchQuery,
                onOpenAuthorProfile = { id -> navController.navigate(NavRoutes.authorProfile(id)) }
            )
        }

        composable(
            route = NavRoutes.AUTHOR_PROFILE,
            arguments = listOf(navArgument("authorId") { type = NavType.StringType })
        ) {
            val vm: AuthorProfileViewModel = hiltViewModel()
            val state by vm.uiState.collectAsStateWithLifecycle()
            AuthorProfileScreen(
                state = state,
                onBack = { navController.popBackStack() },
                onTogglePreferredAlert = vm::togglePreferredAuthorAlert,
                onOpenArticle = { id -> navController.navigate(NavRoutes.reader(id)) },
                onToggleBookmark = vm::toggleBookmark
            )
        }

        composable(NavRoutes.SAVED) {
            val vm: SavedViewModel = hiltViewModel()
            val state by vm.uiState.collectAsStateWithLifecycle()
            SavedScreen(
                state = state,
                onSelectTab = vm::selectTab,
                onOpenArticle = { id -> navController.navigate(NavRoutes.reader(id)) },
                onOpenAuthor = { id -> navController.navigate(NavRoutes.authorProfile(id)) },
                onToggleBookmark = vm::toggleBookmark,
                onClearHistory = vm::clearHistory
            )
        }

        composable(NavRoutes.SEARCH) {
            val vm: SearchViewModel = hiltViewModel()
            val state by vm.uiState.collectAsStateWithLifecycle()
            SearchScreen(
                state = state,
                onBack = { navController.popBackStack() },
                onUpdateQuery = vm::updateQuery,
                onUpdateAuthorQuery = vm::updateAuthorQuery,
                onToggleCategory = vm::toggleCategory,
                onSelectPublisher = vm::selectPublisher,
                onSelectLanguage = vm::selectLanguage,
                onSelectDatePreset = vm::selectDatePreset,
                onSelectSort = vm::selectSort,
                onOpenArticle = { id -> navController.navigate(NavRoutes.reader(id)) },
                onOpenAuthor = { id -> navController.navigate(NavRoutes.authorProfile(id)) },
                onToggleBookmark = vm::toggleBookmark
            )
        }

        composable(
            route = NavRoutes.READER,
            arguments = listOf(navArgument("articleId") { type = NavType.StringType })
        ) {
            val vm: ReaderViewModel = hiltViewModel()
            val state by vm.uiState.collectAsStateWithLifecycle()
            ReaderScreen(
                state = state,
                onBack = { navController.popBackStack() },
                onToggleBookmark = vm::toggleBookmark,
                onOpenAuthor = { id -> navController.navigate(NavRoutes.authorProfile(id)) },
                onUpdateReaderTheme = vm::updateReaderTheme,
                onUpdateFontScale = vm::updateFontScale,
                onUpdateLineSpacing = vm::updateLineSpacing
            )
        }

        composable(NavRoutes.SOURCES) {
            val vm: SourcesViewModel = hiltViewModel()
            val state by vm.uiState.collectAsStateWithLifecycle()
            SourcesScreen(
                state = state,
                onBack = { navController.popBackStack() },
                onTogglePublisher = vm::setPublisherEnabled,
                onSyncPublisher = vm::syncPublisherNow
            )
        }

        composable(NavRoutes.SETTINGS) {
            val vm: SettingsViewModel = hiltViewModel()
            val state by vm.uiState.collectAsStateWithLifecycle()
            SettingsScreen(
                state = state,
                onBack = { navController.popBackStack() },
                onSetLanguage = vm::setLanguage,
                onSetTheme = vm::setThemeMode,
                onSetFontScale = vm::setFontScale,
                onSetLineSpacing = vm::setLineSpacing,
                onSetSyncFrequency = vm::setSyncFrequency,
                onSetWifiOnly = vm::setWifiOnly,
                onSetDataSaver = vm::setDataSaver,
                onToggleEnabledCategory = vm::toggleEnabledCategory,
                onSetNotificationsEnabled = vm::setNotificationsEnabled,
                onNotificationPermissionResult = vm::onNotificationPermissionResult,
                onToggleNotifyCategory = vm::toggleNotifyCategory,
                onTogglePreferredPublisher = vm::togglePreferredPublisher,
                onAddPreferredAuthor = vm::addPreferredAuthor,
                onRemovePreferredAuthor = vm::removePreferredAuthor,
                onSetNotificationFrequency = vm::setNotificationFrequency,
                onSetQuietHours = vm::setQuietHoursEnabled,
                onAddKeywordAlert = vm::addKeywordAlert,
                onDeleteKeywordAlert = vm::deleteKeywordAlert,
                onClearCache = vm::clearNonBookmarkedCache,
                onOpenSources = { navController.navigate(NavRoutes.SOURCES) },
                onOpenMaintenance = { navController.navigate(NavRoutes.MAINTENANCE) },
                onOpenPrivacyPolicy = { navController.navigate(NavRoutes.PRIVACY) },
                onOpenCollectionPolicy = { navController.navigate(NavRoutes.COLLECTION_POLICY) },
                onOpenAbout = { navController.navigate(NavRoutes.ABOUT) }
            )
        }

        composable(NavRoutes.MAINTENANCE) {
            val vm: AdminMaintenanceViewModel = hiltViewModel()
            val state by vm.uiState.collectAsStateWithLifecycle()
            AdminMaintenanceScreen(
                state = state,
                onBack = { navController.popBackStack() },
                onUpdateCutoff = vm::updateCutoffIsoDate,
                onResetCutoff = vm::resetDefaultCutoff,
                onApprovePending = vm::approvePending,
                onRejectPending = vm::rejectPending
            )
        }

        composable(NavRoutes.PRIVACY) {
            PolicyAndAboutScreen(
                type = PolicyScreenType.PRIVACY,
                language = language,
                onBack = { navController.popBackStack() }
            )
        }

        composable(NavRoutes.COLLECTION_POLICY) {
            PolicyAndAboutScreen(
                type = PolicyScreenType.CONTENT_COLLECTION,
                language = language,
                onBack = { navController.popBackStack() }
            )
        }

        composable(NavRoutes.ABOUT) {
            PolicyAndAboutScreen(
                type = PolicyScreenType.ABOUT,
                language = language,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
