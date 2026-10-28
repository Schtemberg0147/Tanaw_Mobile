package com.tanaw.app.feature.workspace

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

internal object ConversationRules {
    fun valid(body:String)=body.trim().codePointCount(0,body.trim().length) in 1..2000
    fun unread(rows:List<JsonObject>,delivery:String)=rows.firstOrNull{it.value("delivery_id")==delivery}?.value("unread")?.toIntOrNull()?:0
}

/** No message body is added to the saved offline workspace. Drafts are scoped to account/assignment. */
@Composable internal fun DriverConversation(
    delivery:String,offline:Boolean,unread:Int?,
    load:suspend (String?)->JsonObject,
    send:suspend (String,String)->Unit,
    read:suspend (List<String>)->Unit,
    changed:()->Unit,
    expectedDriver:String?=null
) {
    var open by rememberSaveable {mutableStateOf(false)}
    var draft by rememberSaveable {mutableStateOf("")}
    var request by rememberSaveable {mutableStateOf<String?>(null)}
    var page by remember {mutableStateOf<JsonObject?>(null)}
    var before by remember {mutableStateOf<String?>(null)}
    var error by remember {mutableStateOf<String?>(null)}
    var notice by remember {mutableStateOf<String?>(null)}
    var busy by remember {mutableStateOf(false)}
    var revision by remember {mutableIntStateOf(0)}
    val scope=rememberCoroutineScope()
    val owner=LocalLifecycleOwner.current
    val currentLoad by rememberUpdatedState(load)
    LaunchedEffect(delivery,expectedDriver,open,before,revision,offline,owner) {
        if(!open || offline)return@LaunchedEffect
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while(true) {
                try {
                    val result=currentLoad(before)
                    check(expectedDriver==null || result.value("driver_id")==expectedDriver){"ASSIGNMENT_CHANGED_RELOAD"}
                    page=result;error=null
                }
                catch(cancelled:CancellationException){throw cancelled}
                catch(_:Exception){page=null;error="Messages unavailable. Check your connection and delivery access, then retry."}
                delay(15.seconds)
            }
        }
    }
    fun action(block:suspend ()->Unit) {
        if(busy || offline)return
        busy=true;error=null;notice=null
        scope.launch {
            try {block()}
            catch(cancelled:CancellationException){throw cancelled}
            catch(_:Exception){error=if(request!=null)"Send unconfirmed. Retry the same message to avoid a duplicate." else "Messages unavailable. Reconnect and retry."}
            finally {busy=false}
        }
    }
    Card(Modifier.fillMaxWidth()) {Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        OutlinedButton(onClick={open=!open}) {Text("${if(open) "Close" else "Open"} office conversation · ${unread?.let{"$it unread"}?:"Unread unavailable"}")}
        if(open) {
            Text("Delivery conversation with authorized office staff. For urgent help, use your normal emergency contact.")
            if(offline)Text("Offline · reconnect to read or send messages.")
            error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
            notice?.let {Text(it)}
            TextButton(onClick={revision++},enabled=!busy && !offline){Text("Retry / refresh messages")}
            if(before!=null)TextButton(onClick={page=null;before=null},enabled=!busy){Text("Latest messages")}
            if(page==null && error==null && !offline)Text("Loading messages…")
            if(!offline)page?.let {data->
                if(data.value("has_more")=="true")TextButton(onClick={before=data.rows("messages").first().value("id");page=null},enabled=!busy){Text("Older messages")}
                if(data.rows("messages").isEmpty())Text("No messages yet.")
                data.rows("messages").forEach {message->
                    Text("${if(message.value("sender_role")=="driver") "Driver" else "Office"} · ${MobilePresentation.time(message.value("created_at"))}")
                    Text(message.value("body"))
                    HorizontalDivider()
                }
                TextButton(onClick={action {read(data.rows("messages").map{it.value("id")});notice="Displayed messages marked read.";changed()}},enabled=!busy && data.rows("messages").isNotEmpty()){Text("Mark displayed messages read")}
                if(data.value("can_send")!="true")Text("This delivery conversation is read-only.")
            }
            OutlinedTextField(value=draft,onValueChange={if(it.length<=4000)draft=it},label={Text("Message to office")},modifier=Modifier.fillMaxWidth(),
                enabled=!busy && !offline && request==null && page?.value("can_send")=="true",minLines=2,maxLines=5)
            if(request!=null)Text("Last send unconfirmed. The message is kept for a safe retry.")
            Button(onClick={action {
                val id=request?:UUID.randomUUID().toString().also{request=it}
                send(id,draft.trim())
                request=null;draft="";notice="Message sent.";before=null;revision++;changed()
            }},enabled=!busy && !offline && (request!=null || (page?.value("can_send")=="true" && ConversationRules.valid(draft)))) {
                Text(if(busy)"Working…" else if(request!=null)"Retry send" else "Send message")
            }
        }
    }}
}
