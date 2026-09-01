package com.example.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.ForestPine
import com.example.ui.theme.ForestPineDark
import com.example.ui.theme.SageGreen
import com.example.ui.theme.SageParchment
import com.example.ui.theme.SlateCanvasDark
import com.example.ui.theme.SlateSurfaceDark

/**
 * The 3 canonical root tab destinations for the Travel Stamp application.
 */
sealed class BottomNavTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    data object Home : BottomNavTab(
        route = Destinations.HOME,
        label = "Home",
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home,
        testTag = "bottom_nav_home"
    )

    data object Passport : BottomNavTab(
        route = Destinations.COLLECTION,
        label = "Passport",
        selectedIcon = Icons.Filled.Collections,
        unselectedIcon = Icons.Outlined.Collections,
        testTag = "bottom_nav_passport"
    )

    data object Settings : BottomNavTab(
        route = Destinations.SETTINGS,
        label = "Settings",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
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
    val isDarkTheme = MaterialTheme.colorScheme.surface == SlateSurfaceDark || 
                      MaterialTheme.colorScheme.background == SlateCanvasDark

    val indicatorColor = if (isDarkTheme) {
        Color(0xFF283A31)
    } else {
        SageParchment
    }

    val selectedIconColor = if (isDarkTheme) {
        Color(0xFFE2F1E9)
    } else {
        ForestPineDark
    }

    val selectedTextColor = if (isDarkTheme) {
        SageGreen
    } else {
        ForestPine
    }

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
                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                        contentDescription = tab.label
                    )
                },
                label = {
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = selectedIconColor,
                    selectedTextColor = selectedTextColor,
                    indicatorColor = indicatorColor,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                ),
                modifier = Modifier.testTag(tab.testTag)
            )
        }
    }
}

