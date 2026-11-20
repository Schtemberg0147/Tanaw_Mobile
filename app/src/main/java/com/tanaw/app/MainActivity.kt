package com.tanaw.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.tanaw.app.feature.workspace.notifications.WorkspaceDeviceNotifier
import com.tanaw.app.feature.workspace.screens.MobileWorkspaceApp
import com.tanaw.app.feature.workspace.viewmodel.MobileWorkspaceViewModel
import com.tanaw.app.ui.theme.TanawTheme
import dagger.hilt.android.AndroidEntryPoint
import io.github.jan.supabase.SupabaseClient
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var supabase: SupabaseClient
    private val viewModel: MobileWorkspaceViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIncomingIntent(intent)
        setContent { TanawTheme { MobileWorkspaceApp(viewModel) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        intent.dataString?.let { uri ->
            viewModel.handleDeepLink(uri)
            intent.data = null
        }
        if (WorkspaceDeviceNotifier.isTrustedNotificationIntent(intent)) {
            val openAlerts = intent.getBooleanExtra(WorkspaceDeviceNotifier.EXTRA_OPEN_ALERTS, false)
            val shipmentId = intent.getStringExtra(WorkspaceDeviceNotifier.EXTRA_SHIPMENT_ID)
            val notificationId = intent.getStringExtra(WorkspaceDeviceNotifier.EXTRA_NOTIFICATION_ID)
            intent.removeExtra(WorkspaceDeviceNotifier.EXTRA_INTERNAL_TOKEN)
            intent.removeExtra(WorkspaceDeviceNotifier.EXTRA_OPEN_ALERTS)
            intent.removeExtra(WorkspaceDeviceNotifier.EXTRA_SHIPMENT_ID)
            intent.removeExtra(WorkspaceDeviceNotifier.EXTRA_NOTIFICATION_ID)
            if (openAlerts || !shipmentId.isNullOrBlank() || !notificationId.isNullOrBlank()) {
                viewModel.handleNotificationIntent(openAlerts, shipmentId, notificationId)
            }
        }
    }
}
