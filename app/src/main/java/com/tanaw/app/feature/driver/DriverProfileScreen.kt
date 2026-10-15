package com.tanaw.app.feature.driver

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tanaw.app.domain.model.ComplianceBadgeState
import com.tanaw.app.domain.model.ComplianceDocumentItem
import com.tanaw.app.domain.model.ComplianceStatus
import com.tanaw.app.domain.model.DriverProfileDetails
import com.tanaw.app.feature.driver.components.DriverBottomNavBar
import com.tanaw.app.feature.driver.components.DriverTopBar
import com.tanaw.app.ui.theme.ExpiredRed
import com.tanaw.app.ui.theme.ExpiredRedBg
import com.tanaw.app.ui.theme.ExpiringOrange
import com.tanaw.app.ui.theme.ExpiringOrangeBg
import com.tanaw.app.ui.theme.NavyBanner
import com.tanaw.app.ui.theme.StatusGreen
import com.tanaw.app.ui.theme.StatusGreenBg
import com.tanaw.app.ui.theme.TanawTheme
import java.time.LocalDate

@Composable
fun DriverProfileScreen(
    viewModel: DriverProfileViewModel = hiltViewModel(),
    onNavigateToRoute: (String) -> Unit = {},
    onLogoutComplete: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.sideEffect.collect { sideEffect ->
            when (sideEffect) {
                DriverProfileSideEffect.NavigateToLogin -> onLogoutComplete()
                is DriverProfileSideEffect.ShowToast -> {}
            }
        }
    }

    DriverProfileContent(
        uiState = uiState,
        onEvent = { viewModel.onEvent(it) },
        onNavigateToRoute = onNavigateToRoute
    )
}

@Composable
fun DriverProfileContent(
    uiState: DriverProfileUiState,
    onEvent: (DriverProfileEvent) -> Unit = {},
    onNavigateToRoute: (String) -> Unit = {}
) {
    Scaffold(
        bottomBar = {
            DriverBottomNavBar(
                selectedTab = "profile",
                onTabSelected = { route -> onNavigateToRoute(route) }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF8FAFC))
                .padding(innerPadding)
        ) {
            when (uiState) {
                is DriverProfileUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = NavyBanner,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }

                is DriverProfileUiState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = uiState.message,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { onEvent(DriverProfileEvent.Retry) },
                            colors = ButtonDefaults.buttonColors(containerColor = NavyBanner),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Retry", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                is DriverProfileUiState.Content -> {
                    val scrollState = rememberScrollState()

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                    ) {
                        // Top Bar
                        DriverTopBar(
                            unreadCount = 1,
                            onNotificationsClick = {}
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Driver Profile Header Section
                        DriverProfileHeaderSection(details = uiState.details)

                        Spacer(modifier = Modifier.height(24.dp))

                        // Section 1: Compliance Status
                        ComplianceStatusSection(
                            docs = uiState.complianceDocs,
                            errorMessage = uiState.docsErrorMessage,
                            onRetryDocs = { onEvent(DriverProfileEvent.RetryDocs) }
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // Section 2: Personal Information
                        PersonalInformationSection(
                            email = uiState.details.email,
                            contact = uiState.details.contactNumber
                        )

                        Spacer(modifier = Modifier.height(32.dp))

                        // Log Out Action Button
                        OutlinedButton(
                            onClick = { onEvent(DriverProfileEvent.LogoutClicked) },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error,
                                containerColor = Color.White
                            ),
                            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.error),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp)
                                .height(48.dp)
                                .testTag("driver_logout_button"),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.ExitToApp,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Log Out",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }
        }
    }
}

// ─── Header Section ────────────────────────────────────────────────────────────
@Composable
fun DriverProfileHeaderSection(
    details: DriverProfileDetails,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Avatar Box: Navy Rounded Square with Initials
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(NavyBanner)
                .testTag("driver_avatar_box"),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = details.initials,
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Driver Full Name
        Text(
            text = details.fullName,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            color = NavyBanner,
            modifier = Modifier.testTag("driver_full_name")
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Vehicle Type · Plate Number
        Text(
            text = details.vehicleTypeNameAndPlate,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF475569),
            modifier = Modifier.testTag("driver_vehicle_plate")
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Driver since Month Year
        Text(
            text = details.driverSinceText,
            fontSize = 13.sp,
            color = Color(0xFF64748B),
            modifier = Modifier.testTag("driver_since_text")
        )
    }
}

// ─── Section 1: Compliance Status ─────────────────────────────────────────────
@Composable
fun ComplianceStatusSection(
    docs: List<ComplianceDocumentItem>,
    errorMessage: String?,
    onRetryDocs: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        // Section Header Row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 12.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Security,
                contentDescription = null,
                tint = NavyBanner,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Compliance Status",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = NavyBanner,
                modifier = Modifier.testTag("compliance_status_header")
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
        ) {
            Column {
                if (errorMessage != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = errorMessage,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onRetryDocs) {
                            Text("Retry", color = NavyBanner, fontWeight = FontWeight.Bold)
                        }
                    }
                } else if (docs.isEmpty()) {
                    Text(
                        text = "No compliance documents on record.",
                        fontSize = 14.sp,
                        color = Color(0xFF64748B),
                        modifier = Modifier.padding(16.dp)
                    )
                } else {
                    docs.forEachIndexed { index, doc ->
                        ComplianceRowItem(doc = doc)
                        if (index < docs.size - 1) {
                            HorizontalDivider(color = Color(0xFFF1F5F9))
                        }
                    }
                }

                // Info Banner Footer
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFEEF2FF))
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = NavyBanner,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Contact your administrator to update compliance documents.",
                            fontSize = 12.sp,
                            color = Color(0xFF334155),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ComplianceRowItem(
    doc: ComplianceDocumentItem,
    modifier: Modifier = Modifier
) {
    val status = doc.status

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = doc.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = NavyBanner
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = status.detailText,
                fontSize = 13.sp,
                color = when (status.state) {
                    ComplianceBadgeState.EXPIRED -> ExpiredRed
                    ComplianceBadgeState.EXPIRING_SOON -> ExpiringOrange
                    ComplianceBadgeState.VALID -> Color(0xFF64748B)
                },
                fontWeight = if (status.isWarning) FontWeight.Bold else FontWeight.Normal
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Badge Chip
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(status.badgeBackgroundColor)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (status.state) {
                ComplianceBadgeState.VALID -> {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = StatusGreen,
                        modifier = Modifier.size(14.dp)
                    )
                }
                ComplianceBadgeState.EXPIRING_SOON -> {
                    Icon(
                        imageVector = Icons.Outlined.Warning,
                        contentDescription = null,
                        tint = ExpiringOrange,
                        modifier = Modifier.size(14.dp)
                    )
                }
                ComplianceBadgeState.EXPIRED -> {
                    Icon(
                        imageVector = Icons.Outlined.Warning,
                        contentDescription = null,
                        tint = ExpiredRed,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = status.badgeLabel,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = status.badgeTextColor
            )
        }
    }
}

// ─── Section 2: Personal Information ───────────────────────────────────────────
@Composable
fun PersonalInformationSection(
    email: String,
    contact: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        // Section Header Row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 12.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Person,
                contentDescription = null,
                tint = NavyBanner,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Personal Information",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = NavyBanner,
                modifier = Modifier.testTag("personal_info_header")
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
        ) {
            Column {
                // EMAIL
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "EMAIL",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF64748B),
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = email,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF334155)
                    )
                }

                HorizontalDivider(color = Color(0xFFF1F5F9))

                // CONTACT
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "CONTACT",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF64748B),
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = contact,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF334155)
                    )
                }
            }
        }
    }
}

// ─── Previews ────────────────────────────────────────────────────────────────
@Preview(showBackground = true, name = "Driver Profile All Valid")
@Composable
fun DriverProfileScreenAllValidPreview() {
    val today = LocalDate.of(2024, 10, 1)

    val sampleDetails = DriverProfileDetails(
        driverId = "drv-1",
        fullName = "Ramon Cruz",
        initials = "RC",
        vehicleTypeNameAndPlate = "10-Wheeler Wing Van · ABC-1234",
        driverSinceText = "Driver since January 2022",
        email = "ramon.cruz@tanaw.ph",
        contactNumber = "09995130476"
    )

    val docs = listOf(
        ComplianceDocumentItem(
            id = "doc-1",
            title = "Professional Driver's License",
            docType = "license",
            documentNumber = "N01-12-345678",
            expiryDate = LocalDate.of(2025, 12, 31),
            status = ComplianceStatus.from(LocalDate.of(2025, 12, 31), today)
        ),
        ComplianceDocumentItem(
            id = "doc-2",
            title = "Medical Certificate",
            docType = "medical",
            documentNumber = "MED-2024-9981",
            expiryDate = LocalDate.of(2025, 6, 30),
            status = ComplianceStatus.from(LocalDate.of(2025, 6, 30), today)
        ),
        ComplianceDocumentItem(
            id = "doc-3",
            title = "NBI Clearance",
            docType = "nbi",
            documentNumber = "NBI-2024-55412",
            expiryDate = LocalDate.of(2024, 11, 15),
            status = ComplianceStatus.from(LocalDate.of(2024, 11, 15), today) // 45 days left
        )
    )

    val sampleUiState = DriverProfileUiState.Content(
        details = sampleDetails,
        complianceDocs = docs
    )

    TanawTheme {
        DriverProfileContent(uiState = sampleUiState)
    }
}

@Preview(showBackground = true, name = "Driver Profile Expired Document")
@Composable
fun DriverProfileScreenExpiredPreview() {
    val today = LocalDate.of(2024, 10, 1)

    val sampleDetails = DriverProfileDetails(
        driverId = "drv-1",
        fullName = "Ramon Cruz",
        initials = "RC",
        vehicleTypeNameAndPlate = "No vehicle assigned",
        driverSinceText = "Driver since January 2022",
        email = "ramon.cruz@tanaw.ph",
        contactNumber = "09995130476"
    )

    val docs = listOf(
        ComplianceDocumentItem(
            id = "doc-1",
            title = "Professional Driver's License",
            docType = "license",
            documentNumber = "N01-12-345678",
            expiryDate = LocalDate.of(2024, 9, 15),
            status = ComplianceStatus.from(LocalDate.of(2024, 9, 15), today) // Expired 16 days ago
        )
    )

    val sampleUiState = DriverProfileUiState.Content(
        details = sampleDetails,
        complianceDocs = docs
    )

    TanawTheme {
        DriverProfileContent(uiState = sampleUiState)
    }
}
