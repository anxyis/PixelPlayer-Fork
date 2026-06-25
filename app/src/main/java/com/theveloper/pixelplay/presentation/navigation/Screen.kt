package com.theveloper.pixelplay.presentation.navigation

import androidx.compose.runtime.Immutable

@Immutable
sealed class Screen(val route: String) {
    object ArchiveDashboard : Screen("archive_dashboard")
    object TopicBrowser : Screen("topic_browser/{channelId}") {
        fun createRoute(channelId: Long) = "topic_browser/$channelId"
    }
    object MediaBrowser : Screen("media_browser/{channelId}/{topicId}") {
        fun createRoute(channelId: Long, topicId: Long) = "media_browser/$channelId/$topicId"
    }
    object Downloads : Screen("downloads")
    object Settings : Screen("settings")
}
