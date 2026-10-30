package com.tanaw.app.feature.workspace

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanaw.app.R
import kotlinx.serialization.json.*

@Composable internal fun DriverProfile(data:JsonObject,busy:Boolean,offline:Boolean,refresh:()->Unit,recover:()->Unit,logout:()->Unit,messages:()->Unit) {
    var panel by rememberSaveable {mutableStateOf<String?>(null)}
    var linkError by remember {mutableStateOf(false)}
    val uri=LocalUriHandler.current
    fun open(value:String) {linkError=runCatching {uri.openUri(value)}.isFailure}
    BackHandler(panel!=null) {panel=null}
    val p=data.obj("profile");val d=data.obj("driver")
    val settings=DriverProfileRules.settings(data)
    val content=DriverProfileRules.content(data)
    val top=remember {BringIntoViewRequester()}
    LaunchedEffect(panel) {top.bringIntoView()}
    Column(Modifier.padding(horizontal=16.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        Spacer(Modifier.height(1.dp).bringIntoViewRequester(top))
        if(panel!=null) {
            TextButton(onClick={panel=null}) {Text("← Back to profile")}
            Text(panel!!,style=MaterialTheme.typography.titleLarge,color=DriverNavy)
            if(linkError)Text("This device could not open the link. Check that a suitable app is installed.",color=MaterialTheme.colorScheme.error)
            if(offline)Text("Offline · these are saved details. Reconnect to check for updates.",color=DriverMuted)
            when(panel) {
                "Help & Support" -> {
                    ProfileCard("Delivery support") {
                        Text("For pickup instructions, delays or unloading questions, open the conversation for the relevant delivery. Include its tracking reference so the office can identify the trip.")
                        Button(onClick=messages) {Text("Open office messages")}
                        Text("Messages are in-app and may not be read immediately. For urgent help, use your established emergency contact.",style=MaterialTheme.typography.bodySmall,color=DriverMuted)
                    }
                    ProfileCard("Profile and documents") {
                        Text("Your personal details, assigned vehicle and compliance records are managed by the administrator. Send the office the field or document that needs correcting. Drivers cannot edit or approve these records here.")
                        Text("Pending or rejected documents are not approved. Expiry warnings use the office’s policy; they do not replace office dispatch checks.")
                    }
                    ProfileCard("Account access") {
                        Text("Use Change Password from Profile to verify your account and update your password. Cancel returns to Profile. If your roster link or account access is unavailable, ask the administrator to check your driver account.")
                        OutlinedButton(onClick=recover,enabled=!busy && !offline) {Text("Change Password")}
                    }
                    ProfileCard("Published office contacts") {
                        val email=content?.value("support_email")?.let(DriverProfileRules::email)
                        val phone=content?.value("support_phone")?.let(DriverProfileRules::phone)
                        val url=content?.value("support_url")?.let(DriverProfileRules::https)
                        if(settings==null)Text("Office contacts unavailable. Refresh your profile to retry.")
                        else if(email==null && phone==null && url==null)Text("The office has not published a support phone, email or website in this app. Use your delivery’s office conversation.")
                        email?.let {TextButton(onClick={open("mailto:$it")}){Text(it)}}
                        phone?.let {TextButton(onClick={open("tel:$it")}){Text("Call $it")}}
                        url?.let {TextButton(onClick={open(it)}){Text("Open support website")}}
                    }
                }
                else -> ProfileCard(panel!!) {
                    val key=if(panel=="Privacy")"privacy_url" else "terms_url"
                    val url=content?.value(key)?.let(DriverProfileRules::https)
                    if(settings==null)Text("Published policy information is unavailable. Refresh your profile to retry.")
                    else if(url==null)Text("The office has not published an approved ${if(panel=="Privacy")"privacy policy" else "terms of service"} destination in this app. Ask your administrator for the current document.")
                    else {
                        Text("Open the office’s published document in your browser. An internet connection may be required.")
                        Button(onClick={open(url)}){Text("Open ${panel!!.lowercase()}")}
                    }
                    if(panel=="Privacy")Text("App behavior: trip tracking shares location while tracking is enabled. Delivery proof and conversations use authorized access. This description is not a replacement for the office’s privacy policy.",style=MaterialTheme.typography.bodySmall,color=DriverMuted)
                }
            }
            OutlinedButton(onClick=refresh,enabled=!busy){Text("Refresh profile information")}
        } else {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                Column {
                    Text("My profile",style=MaterialTheme.typography.headlineSmall,color=DriverNavy)
                    Text("Your account and delivery credentials",style=MaterialTheme.typography.bodySmall,color=DriverMuted)
                }
            }
            Surface(color=DriverNavy,shape=RoundedCornerShape(20.dp)) {
                Row(Modifier.fillMaxWidth().padding(20.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                    Surface(color=Color(0xFF244C70),shape=CircleShape) {
                        Box(Modifier.size(58.dp),contentAlignment=Alignment.Center) {
                            val initials=p.value("full_name").split(' ').filter(String::isNotBlank).take(2).map {it.first().uppercaseChar()}.joinToString("").ifBlank {"D"}
                            Text(initials,color=Color.White,fontWeight=FontWeight.Bold,fontSize=20.sp)
                        }
                    }
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                        Text(p.value("full_name").ifBlank {"Name not recorded"},style=MaterialTheme.typography.titleLarge,color=Color.White)
                        Text("Driver account",color=Color(0xFFDBE8F5),style=MaterialTheme.typography.bodySmall)
                        Text(if(offline)"Saved information · Offline" else "Managed by your office",color=Color(0xFFDBE8F5),style=MaterialTheme.typography.labelSmall)
                    }
                }
            }
            ProfileCard("Personal Information",R.drawable.figma_driver_a35c0) {
                ProfileField("EMAIL",p.value("email"))
                if(p.value("phone").isNotBlank() && p.value("phone")!=d.value("contact_number"))ProfileField("PHONE",p.value("phone"))
                val roster=DriverProfileRules.rosterState(data)
                when(roster) {
                    "ready" -> {
                        ProfileField("CONTACT",d.value("contact_number").ifBlank {p.value("phone")})
                        ProfileField("LICENSE NO.",d.value("license_number"))
                        ProfileField("DRIVER SINCE",d.value("date_hired"))
                    }
                    "empty" -> Text("No driver roster link was returned. Ask the administrator to link your account.",color=MaterialTheme.colorScheme.error)
                    "loading" -> Text("Loading driver profile…")
                    else -> {Text("Driver profile unavailable. Refresh to retry.",color=MaterialTheme.colorScheme.error);TextButton(onClick=refresh,enabled=!busy){Text("Retry driver profile")}}
                }
                Text("Contact your administrator to update personal information.",style=MaterialTheme.typography.bodySmall,color=DriverMuted)
            }
            ProfileCard("Assigned vehicle") {
                when(DriverRecordPresentation.section(data,"driver_trucks")) {
                    "error","unavailable" -> {Text("Assigned vehicle unavailable. Refresh to verify your assignment.",color=MaterialTheme.colorScheme.error);TextButton(onClick=refresh,enabled=!busy){Text("Retry assigned vehicle")}}
                    "loading" -> Text("Loading assigned vehicle…")
                    else -> {
                        val truck=DriverUiRules.truck(data)
                        if(truck==null)Text(DriverRecordPresentation.vehicleLabel(data,null),color=DriverMuted)
                        else Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                            Surface(color=DriverLine,shape=RoundedCornerShape(12.dp)) {Box(Modifier.size(48.dp),contentAlignment=Alignment.Center){DriverAsset(R.drawable.ic_vehicle,24,tint=DriverNavy)}}
                            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                                Text(truck.value("plate_number").ifBlank {"Plate not recorded"},color=DriverNavy,fontWeight=FontWeight.Bold)
                                Text(truck.value("model").ifBlank {"Model not recorded"},style=MaterialTheme.typography.bodySmall,color=DriverMuted)
                            }
                        }
                    }
                }
            }
            ProfileCard("Compliance Status",R.drawable.figma_driver_77550) {
                Text(DriverProfileRules.policyLabel(data),style=MaterialTheme.typography.bodySmall,color=DriverMuted)
                if(settings==null || DriverProfileRules.policyLabel(data).contains("invalid"))TextButton(onClick=refresh,enabled=!busy){Text("Retry warning settings")}
                when(DriverRecordPresentation.section(data,"compliance")) {
                    "error","unavailable" -> {Text("Compliance records unavailable. Refresh or contact the office to verify your documents.",color=MaterialTheme.colorScheme.error);TextButton(onClick=refresh,enabled=!busy){Text("Retry compliance records")}}
                    "loading" -> Text("Loading compliance records…")
                    "empty" -> Text("No compliance documents were returned for your account.",color=DriverMuted)
                    "ready" -> data.rows("compliance").forEach {doc->
                        val label=DriverProfileRules.compliance(doc,settings)
                        val good=label=="VALID"
                        val severe=label in listOf("EXPIRED","REJECTED","CRITICAL")
                        val tint=if(good)Color(0xFF166534)else if(severe)Color(0xFFB42318)else Color(0xFF8A5800)
                        val background=if(good)Color(0xFFEAF6EF)else if(severe)Color(0xFFFFEDEB)else Color(0xFFFFF5DD)
                        Surface(color=Color(0xFFF7F9FD),shape=RoundedCornerShape(12.dp)) {
                            Column(Modifier.fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                                Text(MobilePresentation.status(doc.value("doc_type")).ifBlank {"Document type not recorded"},fontWeight=FontWeight.Bold,color=DriverNavy)
                                Text("Expiry: ${doc.value("expiry_date").ifBlank {"Not recorded"}}",style=MaterialTheme.typography.bodySmall)
                                Surface(color=background,shape=RoundedCornerShape(6.dp)) {
                                    Text(label,Modifier.padding(horizontal=9.dp,vertical=5.dp),color=tint,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
                Text("Contact your administrator to update compliance documents.",style=MaterialTheme.typography.bodySmall,color=DriverMuted)
            }
            ProfileCard("Account & support") {
                OutlinedButton(onClick=recover,enabled=!busy && !offline,modifier=Modifier.fillMaxWidth()){
                    DriverAsset(R.drawable.figma_driver_69f3e,18,description=null);Spacer(Modifier.width(10.dp));Text("Change Password")
                }
                listOf("Help & Support" to R.drawable.figma_driver_991e9,"Terms of Service" to R.drawable.figma_driver_8c779,"Privacy" to R.drawable.figma_driver_8c779).forEach {(title,icon)->
                    TextButton(onClick={panel=title;linkError=false},modifier=Modifier.fillMaxWidth()){
                        DriverAsset(icon,18);Spacer(Modifier.width(10.dp));Text(title,Modifier.weight(1f));DriverAsset(R.drawable.figma_driver_3fa0b,7,12)
                    }
                }
            }
            OutlinedButton(onClick=logout,enabled=!busy,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),shape=RoundedCornerShape(12.dp),border=BorderStroke(1.dp,Color(0xFFF0C9C5))){Text("LOG OUT",color=MaterialTheme.colorScheme.error)}
        }
    }
}

@Composable private fun ProfileCard(title:String,icon:Int?=null,body:@Composable ColumnScope.()->Unit) {
    Surface(color=Color.White,shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,DriverLine)) {
        Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                icon?.let {DriverAsset(it,20)}
                Text(title,style=MaterialTheme.typography.titleMedium,color=DriverNavy)
            }
            body()
        }
    }
}
@Composable private fun ProfileField(label:String,value:String) {
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical=8.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.Top) {
            Text(label,Modifier.weight(.36f),style=MaterialTheme.typography.labelSmall,color=DriverMuted)
            Text(value.ifBlank {"Not recorded"},Modifier.weight(.64f),style=MaterialTheme.typography.bodyMedium,color=DriverNavy)
        }
        HorizontalDivider(color=DriverLine)
    }
}
