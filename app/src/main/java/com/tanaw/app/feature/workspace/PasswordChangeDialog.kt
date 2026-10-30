package com.tanaw.app.feature.workspace

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation

/** Kept above the mounted workspace so cancel returns to the exact Profile entry screen. */
@Composable internal fun PasswordChangeDialog(state:MobileState,cancel:()->Unit,request:()->Unit,
    canVerify:(String)->Boolean,update:(String,String,String)->Unit) {
    var code by remember(state.passwordChangeOwner){mutableStateOf("")}
    var password by remember(state.passwordChangeOwner){mutableStateOf("")}
    var confirm by remember(state.passwordChangeOwner){mutableStateOf("")}
    val sent=state.passwordChangeCodeSent
    DriverTransaction("Change password",state.busy,cancel,
        if(sent)"UPDATE PASSWORD" else "REQUEST VERIFICATION CODE",
        !state.offline && if(sent)canVerify(code) && MobileValidation.password(password,confirm) else state.resendSeconds==0,
        {if(sent)update(code,password,confirm)else request()}) {
        Text("Verify your account with a code before changing your password. After a successful update, sign in again with your new password.")
        OutlinedTextField(state.email,{},readOnly=true,label={Text("Account phone number or email")},modifier=Modifier.fillMaxWidth())
        if(sent) {
            Text("Verification requested for your account.")
            OutlinedTextField(code,{code=it.trim()},enabled=!state.busy,label={Text("Recovery code (or original email link)")},modifier=Modifier.fillMaxWidth())
            OutlinedTextField(password,{password=it},enabled=!state.busy,label={Text("New password (8+ characters)")},
                visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password),modifier=Modifier.fillMaxWidth())
            OutlinedTextField(confirm,{confirm=it},enabled=!state.busy,label={Text("Confirm new password")},
                visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password),modifier=Modifier.fillMaxWidth())
            TextButton(onClick=request,enabled=!state.busy && !state.offline && state.resendSeconds==0) {
                Text(if(state.resendSeconds>0)"Request another code in ${state.resendSeconds}s" else "Request another verification code")
            }
        }
        if(state.offline)Text("Reconnect and refresh before requesting a code or updating your password.")
        state.message?.let{Text(it)}
        state.error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
        TextButton(onClick=cancel,enabled=!state.busy){Text("Back to Profile")}
    }
}
