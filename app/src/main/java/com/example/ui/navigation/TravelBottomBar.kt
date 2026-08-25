package com.example.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * The 3 canonical root tab destinations for the Travel Stamp application.
 */
sealed class BottomNavTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val testTag: String
) {
    data object Home : BottomNavTab(
        route = Destinations.HOME,
        label = "Home",
        icon = Icons.Default.Home,
        testTag = "bottom_nav_home"
    )

    data object Passport : BottomNavTab(
        route = Destinations.COLLECTION,
        label = "Passport",
        icon = Icons.Default.Collections,
        testTag = "bottom_nav_passport"
    )

    data object Settings : BottomNavTab(
        route = Destinations.SETTINGS,
        label = "Settings",
        icon = Icons.Default.Settings,
        testTag = "bottom_nav_settings"
    )

    companion object {
        val tabs = listOf(Home, Passport, Settings)
    }
}

@Composable
fun TravelBottomBar(
    currentRoute: String?,
    onNavigateToTab: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier.testTag("travel_bottom_navigation_bar"),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 3.dp,
        windowInsets = NavigationBarDefaults.windowInsets
    ) {
        BottomNavTab.tabs.forEach { tab ->
            val selected = currentRoute == tab.route

            NavigationBarItem(
                selected = selected,
                onClick = { onNavigateToTab(tab.route) },
                icon = {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label
                    )
                },
                label = {
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.testTag(tab.testTag)
            )
        }
    }
}
