package com.tanaw.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.tanaw.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PODScreen(
    bookingId: String,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Proof of Delivery") }, navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(painter = painterResource(id = R.drawable.ic_arrow_back), contentDescription = "Back")
                }
            })
        }
    ) { innerPadding ->
        Column(modifier = Modifier
            .padding(innerPadding)
            .fillMaxSize()
            .padding(16.dp)
        ) {
            // Static POD placeholder image
            Image(
                painter = painterResource(id = R.drawable.map_mock),
                contentDescription = "POD image",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text("Booking: $bookingId", style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(6.dp))
            Text("Signed by: Juan Dela Cruz", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
    }
}
