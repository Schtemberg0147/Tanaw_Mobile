package com.tanaw.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.tanaw.app.feature.workspace.*
import io.github.jan.supabase.auth.auth
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Explicit opt-in credentials are runner arguments only; never checked in or printed. */
class LiveDriverLoginTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun viewModel():MobileWorkspaceViewModel {
        lateinit var vm:MobileWorkspaceViewModel
        compose.runOnUiThread {vm=ViewModelProvider(compose.activity)[MobileWorkspaceViewModel::class.java]}
        compose.waitUntil(30000) {!vm.state.value.busy}
        return vm
    }
    @Test fun requestOwnedPhoneRecovery() {
        val args=InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("allowPhoneRecovery")=="request")
        val phone=requireNotNull(args.getString("driverPhone"))
        val vm=viewModel()
        compose.runOnUiThread {vm.page("recover");vm.resetEmail(phone)}
        compose.waitUntil(60000) {!vm.state.value.busy}
        assertNull("Phone recovery request failed",vm.state.value.error)
        assertEquals("recover_code",vm.state.value.page)
        assertNotNull(WorkspaceRecoveryStore(compose.activity,BuildConfig.SUPABASE_URL).read("challenge",RecoveryPolicy.CHALLENGE_AGE))
    }
    @Test fun completeOwnedPhoneRecoveryAndRouteDriver() {
        val args=InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("allowPhoneRecovery")=="complete")
        val code=requireNotNull(args.getString("recoveryCode"))
        val password=requireNotNull(args.getString("driverPassword"))
        val phone=requireNotNull(args.getString("driverPhone"))
        val vm=viewModel()
        assertEquals("recover_code",vm.state.value.page)
        compose.runOnUiThread {vm.recover(code,password,password)}
        compose.waitUntil(60000) {!vm.state.value.busy}
        assertNull("Phone password recovery failed",vm.state.value.error)
        assertEquals("login",vm.state.value.page)
        assertNull(WorkspaceRecoveryStore(compose.activity,BuildConfig.SUPABASE_URL).read("challenge",RecoveryPolicy.CHALLENGE_AGE))
        compose.runOnUiThread {vm.login(phone,password)}
        compose.waitUntil(60000) {!vm.state.value.busy}
        assertNull("Recovered phone login failed",vm.state.value.error)
        assertEquals("driver",vm.state.value.data!!.obj("profile").value("role"))
        compose.onNodeWithText("PROFILE").assertExists()
    }
    @Test fun emailSignInUsesServerDriverProfileAndRestoresColdSession() {
        val args=InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("allowDriverLogin")=="true")
        val email=requireNotNull(args.getString("driverEmail"))
        val password=requireNotNull(args.getString("driverPassword"))
        lateinit var vm:MobileWorkspaceViewModel
        compose.runOnUiThread {
            vm=ViewModelProvider(compose.activity)[MobileWorkspaceViewModel::class.java]
        }
        compose.waitUntil(30000) {!vm.state.value.busy}
        compose.runOnUiThread {vm.page("login");vm.login(email,password)}
        compose.waitUntil(60000) { (!vm.state.value.busy) && ((vm.state.value.page == "workspace") || (vm.state.value.error != null)) }
        assertNull("Driver login failed",vm.state.value.error)
        assertEquals("workspace",vm.state.value.page)
        assertEquals("driver",vm.state.value.data!!.obj("profile").value("role"))
        val owner=compose.activity.supabase.auth.currentUserOrNull()!!.id
        assertEquals(owner,vm.state.value.data!!.obj("profile").value("id"))
        compose.onNodeWithText("PROFILE").assertExists()
        compose.activityRule.scenario.recreate()
        compose.waitUntil(60000) {compose.onAllNodesWithText("PROFILE").fetchSemanticsNodes().isNotEmpty()}
        assertEquals(owner,compose.activity.supabase.auth.currentUserOrNull()!!.id)
    }
}
