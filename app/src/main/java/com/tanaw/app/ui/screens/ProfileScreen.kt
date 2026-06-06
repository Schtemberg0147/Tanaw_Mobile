package com.tanaw.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanaw.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onEditProfilePhoto: () -> Unit = {},
    onEditField: (fieldKey: String, currentValue: String) -> Unit = { _, _ -> },
    onMenuClick: (menuId: String) -> Unit = {},
    onLogout: () -> Unit = {},
    selectedTab: Int = 3, // 0=Book,1=Track,2=History,3=Profile
    onBottomNavSelected: (index: Int) -> Unit = {}
) {
    // sample state — replace with real user data from ViewModel
    var fullName by rememberSaveable { mutableStateOf("Maria Santos") }
    var email by rememberSaveable { mutableStateOf("maria.santos@gmail.com") }
    var contact by rememberSaveable { mutableStateOf("0918 234 5678") }
    var defaultAddress by rememberSaveable { mutableStateOf("Cabanatuan City, NE") }
    val memberSince = "MEMBER SINCE JANUARY 2025"

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "My Profile",
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.fillMaxWidth()
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Header card with avatar and basic info
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Avatar with camera overlay
                    Box(modifier = Modifier.size(72.dp), contentAlignment = Alignment.BottomEnd) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = initialsFromName(fullName),
                                fontWeight = FontWeight.Bold,
                                fontSize = 24.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        IconButton(
                            onClick = onEditProfilePhoto,
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(2.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_camera),
                                contentDescription = "Edit photo",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = fullName,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = email,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = memberSince,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Personal information section
            Text(
                "Personal Information",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))

            ProfileField(label = "Full Name", value = fullName, onEdit = { onEditField("fullName", fullName) })
            ProfileField(label = "Email", value = email, onEdit = { onEditField("email", email) })
            ProfileField(label = "Contact", value = contact, onEdit = { onEditField("contact", contact) })
            ProfileField(label = "Default Address", value = defaultAddress, onEdit = { onEditField("address", defaultAddress) })

            Spacer(modifier = Modifier.height(16.dp))

            // Menu options
            MenuItemRow(text = "My Bookings", onClick = { onMenuClick("my_bookings") })
            MenuItemRow(text = "Saved Addresses", onClick = { onMenuClick("saved_addresses") })
            MenuItemRow(text = "Change Password", onClick = { onMenuClick("change_password") })
            MenuItemRow(text = "Terms of Service", onClick = { onMenuClick("tos") })
            MenuItemRow(text = "Help & Support", onClick = { onMenuClick("help_support") })

            Spacer(modifier = Modifier.weight(1f))

            // Logout button
            Button(
                onClick = onLogout,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    "Log Out",
                    color = MaterialTheme.colorScheme.onError,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ProfileField(label: String, value: String, onEdit: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun MenuItemRow(text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onBackground
        )
        Icon(
            painter = painterResource(id = R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun BottomNavigationBar(selectedIndex: Int, onSelect: (Int) -> Unit) {
    NavigationBar {
        NavigationBarItem(
            selected = selectedIndex == 0,
            onClick = { onSelect(0) },
            icon = {
                Icon(
                    painter = painterResource(id = R.drawable.ic_nav_book),
                    contentDescription = "Book",
                    tint = if (selectedIndex == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            label = { Text("BOOK") }
        )
        NavigationBarItem(
            selected = selectedIndex == 1,
            onClick = { onSelect(1) },
            icon = {
                Icon(
                    painter = painterResource(id = R.drawable.ic_nav_track),
                    contentDescription = "Track",
                    tint = if (selectedIndex == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            label = { Text("TRACK") }
        )
        NavigationBarItem(
            selected = selectedIndex == 2,
            onClick = { onSelect(2) },
            icon = {
                Icon(
                    painter = painterResource(id = R.drawable.ic_nav_history),
                    contentDescription = "History",
                    tint = if (selectedIndex == 2) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            label = { Text("HISTORY") }
        )
        NavigationBarItem(
            selected = selectedIndex == 3,
            onClick = { onSelect(3) },
            icon = {
                Icon(
                    painter = painterResource(id = R.drawable.ic_nav_profile),
                    contentDescription = "Profile",
                    tint = if (selectedIndex == 3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            label = { Text("PROFILE") }
        )
    }
}

private fun initialsFromName(name: String): String {
    return name.split(" ")
        .filter { it.isNotBlank() }
        .mapNotNull { it.firstOrNull()?.toString() }
        .take(2)
        .joinToString("")
        .uppercase()
}
