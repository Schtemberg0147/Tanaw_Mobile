package com.tanaw.app.feature.driver.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanaw.app.R
import com.tanaw.app.feature.workspace.DriverAsset
import com.tanaw.app.feature.workspace.DriverButton
import com.tanaw.app.feature.workspace.DriverNavy
import java.io.ByteArrayOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun AddTripExpenseSheet(deliveryId:String,trackingNumber:String,isSubmitting:Boolean,errorMessage:String?,onDismiss:()->Unit,
    onSubmit:(category:String,amount:Double,receiptBytes:ByteArray?)->Unit,modifier:Modifier=Modifier) {
    val context=LocalContext.current
    var category by rememberSaveable(deliveryId){mutableStateOf("")}
    var amount by rememberSaveable(deliveryId){mutableStateOf("")}
    var receipt by remember(deliveryId){mutableStateOf<ByteArray?>(null)}
    var receiptError by remember {mutableStateOf<String?>(null)}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {uri->
        if(uri!=null) {
            receiptError=null;receipt=null
            try {
                require(context.contentResolver.getType(uri) in listOf("image/jpeg","image/png")) {"Choose a JPG or PNG receipt."}
                val bytes=context.contentResolver.openInputStream(uri)?.use {input->
                    val output=ByteArrayOutputStream();val buffer=ByteArray(8192)
                    while(true) {val count=input.read(buffer);if(count<0)break;require(output.size()+count<=5*1024*1024){"Receipt must be 5 MB or smaller."};output.write(buffer,0,count)}
                    output.toByteArray()
                }
                require(bytes!=null && bytes.isNotEmpty()) {"Unable to read the receipt. Choose it again."}
                receipt=bytes
            }catch(error:Exception){receiptError=error.message?:"Unable to read the receipt."}
        }
    }
    ModalBottomSheet(onDismissRequest={if(!isSubmitting)onDismiss()},sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),
        containerColor=Color.White,shape=RoundedCornerShape(topStart=24.dp,topEnd=24.dp),modifier=modifier.testTag("add_trip_expense_sheet")) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal=24.dp).padding(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("Add Trip Expense",fontSize=18.sp,fontWeight=FontWeight.Bold,color=DriverNavy,modifier=Modifier.weight(1f))
                Text(trackingNumber,fontSize=10.sp,color=DriverNavy)
                TextButton(onClick=onDismiss,enabled=!isSubmitting){Text("×",fontSize=24.sp)}
            }
            HorizontalDivider(color=Color(0xFFE7EEFF))
            Text("WHAT WAS THE EXPENSE?",fontSize=10.sp)
            OutlinedTextField(category,{category=it},placeholder={Text("e.g. Loading fee, parking, etc.")},enabled=!isSubmitting,
                modifier=Modifier.fillMaxWidth().testTag("expense_category_input"),shape=RoundedCornerShape(0.dp),singleLine=true,
                colors=OutlinedTextFieldDefaults.colors(unfocusedContainerColor=Color(0xFFF0F3FF),unfocusedBorderColor=Color(0xFFCDD7F7)))
            Text("AMOUNT",fontSize=10.sp)
            OutlinedTextField(amount,{if(it.isEmpty() || it.matches(Regex("^\\d*\\.?\\d{0,2}$")))amount=it},placeholder={Text("0.00",fontSize=24.sp)},
                prefix={Text("₱ ",fontSize=24.sp,fontWeight=FontWeight.Bold,color=DriverNavy)},enabled=!isSubmitting,
                modifier=Modifier.fillMaxWidth().testTag("expense_amount_input"),singleLine=true,shape=RoundedCornerShape(0.dp),
                keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),
                colors=OutlinedTextFieldDefaults.colors(unfocusedContainerColor=Color(0xFFF0F3FF),unfocusedBorderColor=Color(0xFFCDD7F7)))
            Text("ATTACH RECEIPT (OPTIONAL)",fontSize=10.sp)
            Surface(onClick={picker.launch("image/*")},enabled=!isSubmitting,color=Color(0xFFF0F3FF),border=BorderStroke(1.dp,Color(0xFFC3C6CF)),modifier=Modifier.testTag("receipt_photo_selector")) {
                Column(Modifier.fillMaxWidth().heightIn(min=150.dp).padding(16.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                    DriverAsset(R.drawable.figma_driver_ceb65,48,46)
                    Text(if(receipt!=null)"Receipt attached · choose another" else "Upload receipt photo",fontSize=14.sp,color=DriverNavy)
                    Text("JPG or PNG · Max 5MB",fontSize=12.sp,color=Color(0xFF73777F))
                }
            }
            if(receipt!=null)TextButton(onClick={receipt=null},enabled=!isSubmitting){Text("Remove receipt")}
            (receiptError?:errorMessage)?.let {Text(it,color=MaterialTheme.colorScheme.error,fontSize=12.sp)}
            HorizontalDivider(color=Color(0xFFE7EEFF))
            DriverButton(if(isSubmitting)"Submitting…" else "Submit Expense",!isSubmitting && category.trim().length in 2..200 && amount.toDoubleOrNull()?.let{it.isFinite() && it>0 && it<=1000000}==true) {
                onSubmit(category.trim(),amount.toDouble(),receipt)
            }
            Text("Submitted expenses are available for office review.",fontSize=10.sp,color=Color(0xFF73777F))
        }
    }
}
