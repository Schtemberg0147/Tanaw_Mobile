package com.tanaw.app.ui.components

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.tanaw.app.R

// ─── Bottom Nav Items ─────────────────────────────────────────────────────────
sealed class BottomNavItem(
    val route   : String,
    val label   : String,
    val iconRes : Int
) {
    object Book    : BottomNavItem("home",    "BOOK",    R.drawable.ic_nav_book)
    object Track   : BottomNavItem("track",   "TRACK",   R.drawable.ic_nav_track)
    object History : BottomNavItem("history", "HISTORY", R.drawable.ic_nav_history)
    object Profile : BottomNavItem("profile", "PROFILE", R.drawable.ic_nav_profile)
}

val bottomNavItems = listOf(
    BottomNavItem.Book,
    BottomNavItem.Track,
    BottomNavItem.History,
    BottomNavItem.Profile,
)

// ─── Bottom Nav Bar ───────────────────────────────────────────────────────────
@Composable
fun TanawBottomNavBar(navController: NavController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor   = MaterialTheme.colorScheme.primary,
        tonalElevation = 8.dp,
        modifier       = Modifier.height(64.dp)
    ) {
        bottomNavItems.forEach { item ->
            val isSelected = currentRoute == item.route
            val activeColor = MaterialTheme.colorScheme.primary
            val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant

            NavigationBarItem(
                selected = isSelected,
                onClick  = {
                    if (currentRoute != item.route) {
                        navController.navigate(item.route) {
                            popUpTo(navController.graph.startDestinationId) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState    = true
                        }
                    }
                },
                icon = {
                    Icon(
                        painter            = painterResource(id = item.iconRes),
                        contentDescription = item.label,
                        modifier           = Modifier.size(22.dp),
                        tint               = if (isSelected) activeColor else inactiveColor
                    )
                },
                label = {
                    Text(
                        text          = item.label,
                        fontSize      = 9.sp,
                        fontWeight    = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color         = if (isSelected) activeColor else inactiveColor,
                        letterSpacing = 0.5.sp
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor   = activeColor,
                    unselectedIconColor = inactiveColor,
                    selectedTextColor   = activeColor,
                    unselectedTextColor = inactiveColor,
                    indicatorColor      = activeColor.copy(alpha = 0.12f)
                )
            )
        }
    }
}
