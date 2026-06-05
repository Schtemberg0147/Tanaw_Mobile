import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Button
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.material.Scaffold
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment.Companion.CenterVertically

@Composable
fun SimpleTopBar(title: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(Color(0xFF0B63D6)),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = title,
            color = Color.White,
            modifier = Modifier.padding(start = 16.dp),
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun BottomNavigationBar(selectedIndex: Int = 0, onSelect: (Int) -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(Color.White),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val items = listOf("Book", "Track", "History", "Profile")
        items.forEachIndexed { index, label ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clickable { onSelect(index) }
                    .padding(vertical = 6.dp)
            ) {
                Text(
                    text = label,
                    color = if (index == selectedIndex) Color(0xFF0B63D6) else Color.Gray,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun ProfileScreen(
    onEditProfilePhoto: () -> Unit = {},
    onEditField: (fieldKey: String, currentValue: String) -> Unit = { _, _ -> },
    onMenuClick: (menuId: String) -> Unit = {},
    onLogout: () -> Unit = {},
    selectedTab: Int = 3, // 0=Book,1=Track,2=History,3=Profile
    onBottomNavSelected: (index: Int) -> Unit = {}
) {
    // sample state – replace with real user data from ViewModel
    var fullName by rememberSaveable { mutableStateOf("Maria Santos") }
    var email by rememberSaveable { mutableStateOf("maria.santos@gmail.com") }
    var contact by rememberSaveable { mutableStateOf("0918 234 5678") }
    var defaultAddress by rememberSaveable { mutableStateOf("Cabanatuan City, NE") }
    val memberSince = "MEMBER SINCE JANUARY 2025"

    Scaffold(
        topBar = {
            SimpleTopBar(title = "My Profile")
        },
        bottomBar = {
            BottomNavigationBar(selectedIndex = selectedTab, onSelect = onBottomNavSelected)
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.Start
        ) {
            // Profile photo row
            Row(
                verticalAlignment = CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Placeholder circle for profile image
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(CircleShape)
                        .background(Color.LightGray)
                        .clickable { onEditProfilePhoto() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "IMG", color = Color.DarkGray)
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(text = fullName, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = memberSince, fontSize = 12.sp, color = Color.Gray)
                }

                IconButton(onClick = { onMenuClick("profile_menu") }) {
                    Icon(
                        painter = painterResource(id = android.R.drawable.ic_menu_more),
                        contentDescription = "Menu",
                        tint = Color.DarkGray
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Fields
            ProfileField(label = "Email", value = email) { onEditField("email", email) }
            ProfileField(label = "Contact", value = contact) { onEditField("contact", contact) }
            ProfileField(label = "Address", value = defaultAddress) { onEditField("address", defaultAddress) }

            Spacer(modifier = Modifier.height(24.dp))

            Button(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
                Text(text = "Log out")
            }
        }
    }
}

@Composable
private fun ProfileField(label: String, value: String, onEdit: () -> Unit) {
    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 8.dp)) {
        Text(text = label, fontSize = 12.sp, color = Color.Gray)
        Row(
            verticalAlignment = CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
        ) {
            Text(
                text = value,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Edit",
                color = Color(0xFF0B63D6),
                modifier = Modifier
                    .clickable { onEdit() }
                    .padding(8.dp),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
