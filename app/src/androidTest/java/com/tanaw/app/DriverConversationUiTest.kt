package com.tanaw.app

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.tanaw.app.feature.workspace.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class DriverConversationUiTest {
    @get:Rule val compose=createComposeRule()
    private fun page(canSend:Boolean=true,more:Boolean=false)=buildJsonObject {
        put("driver_id","driver");put("can_send",canSend);put("has_more",more)
        put("messages",buildJsonArray {add(buildJsonObject {put("id","42");put("sender_role","staff");put("body","Use the north entrance");put("created_at","2026-10-28T12:00:00Z")})})
    }
    private fun open() {compose.onNodeWithText("Open office conversation",substring=true).performClick();compose.waitForIdle()}
    @Test fun lostSendResponseRetriesSameIdAndBodyThenClearsDraft() {
        val calls=mutableListOf<Pair<String,String>>()
        compose.setContent {DriverTheme {Column {DriverConversation("delivery",false,1,{page()}, {id,body->calls.add(id to body);if(calls.size==1)error("response lost")},{},{})}}}
        open();compose.onNodeWithText("Use the north entrance").assertExists()
        compose.onNodeWithText("Message to office").performTextInput("Arriving at gate")
        compose.onNodeWithText("Send message").performClick();compose.waitForIdle()
        compose.onNodeWithText("Send unconfirmed.",substring=true).assertExists()
        compose.onNodeWithText("Message to office").assertIsNotEnabled()
        compose.onNodeWithText("Retry send").performClick();compose.waitForIdle()
        assertEquals(2,calls.size);assertEquals(calls[0],calls[1])
        compose.onNodeWithText("Message sent.").assertExists();compose.onNodeWithText("Message to office").assertIsEnabled()
    }
    @Test fun unreadOnlyClearsExplicitDisplayedIdsAndOlderPageHasCursor() {
        val reads=mutableListOf<List<String>>();val cursors=mutableListOf<String?>()
        compose.setContent {DriverTheme {Column {DriverConversation("delivery",false,1,{cursor->cursors.add(cursor);page(more=cursor==null)},{_,_->},{reads.add(it)},{})}}}
        open();assertTrue(reads.isEmpty())
        compose.onNodeWithText("Mark displayed messages read").performClick();compose.waitForIdle();assertEquals(listOf("42"),reads.single())
        compose.onNodeWithText("Older messages").performClick();compose.waitForIdle();assertEquals("42",cursors.last())
        compose.onNodeWithText("Latest messages").assertExists()
    }
    @Test fun offlineAndClosedConversationsCannotCompose() {
        var offline by mutableStateOf(true)
        compose.setContent {DriverTheme {Column {DriverConversation("delivery",offline,null,{page(false)},{_,_->error("must not send")},{},{})}}}
        open();compose.onNodeWithText("Offline · reconnect",substring=true).assertExists()
        compose.onNodeWithText("Send message").assertIsNotEnabled()
        compose.runOnIdle{offline=false};compose.waitForIdle()
        compose.onNodeWithText("This delivery conversation is read-only.").assertExists()
        compose.onNodeWithText("Message to office").assertIsNotEnabled()
    }
    @Test fun loadFailureHasRetryAndDoesNotClaimEmpty() {
        var calls=0
        compose.setContent {DriverTheme {Column {DriverConversation("delivery",false,null,{if(calls++==0)error("network");page()},{_,_->},{},{})}}}
        open();compose.onNodeWithText("Messages unavailable.",substring=true).assertExists()
        compose.onNodeWithText("No messages yet.").assertDoesNotExist()
        compose.onNodeWithText("Retry / refresh messages").performClick();compose.waitForIdle()
        compose.onNodeWithText("Use the north entrance").assertExists()
    }
    @Test fun unreadInboxOpensExactDeliveryEvenOutsideLoadedTripList() {
        val data=buildJsonObject {
            put("profile",buildJsonObject{put("id","fixture-owner");put("role","driver")})
            put("shipments",JsonArray(emptyList()));put("deliveries",JsonArray(emptyList()))
        }
        val unread=buildJsonArray {add(buildJsonObject{put("delivery_id","exact-unread-delivery");put("driver_id","driver");put("tracking_number","UI-MSG-001");put("unread",3)})}
        compose.setContent {DriverTheme {DriverWorkspaceScreen(MobileState(page="workspace",conversationUnread=unread),data,
            {},{},{},{},{},{_,_,_->},{_,_->},{_,_,_,_,_,_->},conversation={d->Text("Opened ${d.value("id")}")})}}
        compose.onNodeWithText("Messages (3)").performClick()
        compose.onNodeWithText("Delivery UI-MSG-001").assertExists()
        compose.onNodeWithText("Opened exact-unread-delivery").assertExists()
    }
    @Test fun staleDriverThreadDoesNotShowReplacementMessages() {
        compose.setContent {DriverTheme {Column {DriverConversation("delivery",false,0,{page()},{_,_->},{},{},expectedDriver="former-driver")}}}
        open();compose.onNodeWithText("Messages unavailable.",substring=true).assertExists()
        compose.onNodeWithText("Use the north entrance").assertDoesNotExist()
        compose.onNodeWithText("Send message").assertIsNotEnabled()
    }
}
