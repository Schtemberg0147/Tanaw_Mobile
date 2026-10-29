package com.tanaw.app.feature.workspace

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
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
    fun timestamp(value:String):String = try {
        DateTimeFormatter.ofPattern("MMM d · h:mm a",Locale.ENGLISH).withZone(ZoneId.of("Asia/Manila"))
            .format(OffsetDateTime.parse(value.replace(' ','T')).toInstant()) + " PHT"
    } catch (_:Exception) { "Time unavailable" }
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
    expectedDriver:String?=null,
    initiallyOpen:Boolean=false,
    reference:String=""
) {
    var open by rememberSaveable {mutableStateOf(initiallyOpen)}
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
    val historyScroll=rememberScrollState()
    val messages=page?.rows("messages").orEmpty()
    LaunchedEffect(open,before,messages.lastOrNull()?.value("id")) {
        if(open && before==null)historyScroll.animateScrollTo(historyScroll.maxValue)
    }
    Surface(modifier=Modifier.fillMaxWidth().then(if(initiallyOpen) Modifier.fillMaxHeight() else Modifier.heightIn(max=680.dp)),
        color=Color.White,shape=RoundedCornerShape(16.dp)) {
        Column {
            if(!initiallyOpen)TextButton(onClick={open=!open},modifier=Modifier.padding(horizontal=8.dp)) {
                Text("${if(open) "Close" else "Open"} office conversation · ${unread?.let{"$it unread"}?:"Unread unavailable"}")
            }
            if(open) {
                Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Office",color=DriverNavy,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium)
                        if(reference.isNotBlank())Text(reference,style=MaterialTheme.typography.labelMedium,color=DriverMuted)
                        Text(if(offline) "Offline" else unread?.let {"$it unread · Delivery updates"} ?: "Unread unavailable",style=MaterialTheme.typography.bodySmall,color=DriverMuted)
                    }
                    TextButton(onClick={revision++},enabled=!busy && !offline){Text("Retry / refresh messages",fontSize=11.sp)}
                }
                HorizontalDivider(color=DriverLine)
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(historyScroll).padding(14.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    if(offline)Text("Offline · reconnect to read or send messages.",color=DriverMuted)
                    error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
                    notice?.let {Text(it,color=DriverNavy,style=MaterialTheme.typography.bodySmall)}
                    if(before!=null)TextButton(onClick={page=null;before=null},enabled=!busy){Text("Latest messages")}
                    if(page==null && error==null && !offline)Text("Loading messages…",color=DriverMuted)
                    if(!offline)page?.let {data->
                        if(data.value("has_more")=="true")TextButton(onClick={before=data.rows("messages").first().value("id");page=null},enabled=!busy){Text("Older messages")}
                        if(messages.isEmpty())Column(Modifier.fillMaxWidth().padding(vertical=24.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                            Text("No messages yet.",fontWeight=FontWeight.Bold,color=DriverNavy)
                            Text("Send the office a delivery update below.",color=DriverMuted,style=MaterialTheme.typography.bodySmall)
                        }
                        messages.forEach {message->
                            val mine=message.value("sender_role")=="driver"
                            val textColor=if(mine)Color.White else DriverNavy
                            Row(Modifier.fillMaxWidth(),horizontalArrangement=if(mine)Arrangement.End else Arrangement.Start) {
                                Surface(color=if(mine)DriverNavy else Color(0xFFF0F3FA),
                                    shape=RoundedCornerShape(topStart=16.dp,topEnd=16.dp,bottomEnd=if(mine)4.dp else 16.dp,bottomStart=if(mine)16.dp else 4.dp),
                                    modifier=Modifier.fillMaxWidth(.88f)) {
                                    Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                                        Text(if(mine)"You" else "Office",color=textColor,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.labelMedium)
                                        Text(message.value("body"),color=textColor,style=MaterialTheme.typography.bodyLarge.copy(lineHeight=24.sp))
                                        Text(ConversationRules.timestamp(message.value("created_at")),color=if(mine)Color(0xFFD5E4F8)else DriverMuted,style=MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                        TextButton(onClick={action {read(messages.map{it.value("id")});notice="Displayed messages marked read.";changed()}},enabled=!busy && messages.isNotEmpty()) {Text("Mark displayed messages read",fontSize=12.sp)}
                        if(data.value("can_send")!="true")Text("This delivery conversation is read-only.",color=DriverMuted)
                    }
                }
                HorizontalDivider(color=DriverLine)
                Column(Modifier.fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    if(request!=null)Text("Last send unconfirmed. The message is kept for a safe retry.",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
                    OutlinedTextField(value=draft,onValueChange={if(it.length<=4000)draft=it},label={Text("Message to office")},placeholder={Text("Write a delivery update…")},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp),
                        enabled=!busy && !offline && request==null && page?.value("can_send")=="true",minLines=1,maxLines=3)
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text("For urgent help, use your emergency contact.",modifier=Modifier.weight(1f),style=MaterialTheme.typography.labelSmall,color=DriverMuted)
                        Button(onClick={action {
                            val id=request?:UUID.randomUUID().toString().also{request=it}
                            send(id,draft.trim())
                            request=null;draft="";notice="Message sent.";before=null;revision++;changed()
                        }},enabled=!busy && !offline && (request!=null || (page?.value("can_send")=="true" && ConversationRules.valid(draft)))) {
                            Text(if(busy)"Working…" else if(request!=null)"Retry send" else "Send message")
                        }
                    }
                }
            }
        }
    }
}
