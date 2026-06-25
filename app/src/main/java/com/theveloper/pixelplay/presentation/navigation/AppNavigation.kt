package com.theveloper.pixelplay.presentation.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.theveloper.pixelplay.presentation.archive.ArchiveDashboardScreen
import com.theveloper.pixelplay.presentation.archive.DownloadsScreen
import com.theveloper.pixelplay.presentation.archive.MediaBrowserScreen
import com.theveloper.pixelplay.presentation.archive.TopicBrowserScreen

@Composable
fun AppNavigation(
    navController: NavHostController,
    paddingValues: PaddingValues
) {
    NavHost(
        navController = navController,
        startDestination = Screen.ArchiveDashboard.route,
        modifier = Modifier.padding(paddingValues),
        enterTransition = {
            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(300))
        },
        exitTransition = {
            slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(300))
        },
        popEnterTransition = {
            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(300))
        },
        popExitTransition = {
            slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(300))
        }
    ) {
        composable(Screen.ArchiveDashboard.route) {
            ArchiveDashboardScreen(navController = navController)
        }
        
        composable(
            route = Screen.TopicBrowser.route,
            arguments = listOf(navArgument("channelId") { type = NavType.LongType })
        ) { backStackEntry ->
            val channelId = backStackEntry.arguments?.getLong("channelId") ?: 0L
            TopicBrowserScreen(navController = navController, channelId = channelId)
        }
        
        composable(
            route = Screen.MediaBrowser.route,
            arguments = listOf(
                navArgument("channelId") { type = NavType.LongType },
                navArgument("topicId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val channelId = backStackEntry.arguments?.getLong("channelId") ?: 0L
            val topicId = backStackEntry.arguments?.getLong("topicId") ?: 0L
            MediaBrowserScreen(navController = navController, channelId = channelId, topicId = topicId)
        }
        
        composable(Screen.Downloads.route) {
            DownloadsScreen(navController = navController)
        }
        
        composable(Screen.Settings.route) {
            // Placeholder for now
        }
    }
}
