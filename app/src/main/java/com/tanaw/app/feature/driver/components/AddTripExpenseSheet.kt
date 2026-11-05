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
import com.tanaw.app.feature.workspace.DriverEvidence
import com.tanaw.app.feature.workspace.DriverNavy
import com.tanaw.app.feature.workspace.ExpenseDraft
import com.tanaw.app.feature.workspace.ExpenseDraftStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun AddTripExpenseSheet(deliveryId:String,trackingNumber:String,isSubmitting:Boolean,errorMessage:String?,onDismiss:()->Unit,
    onSubmit:(category:String,amount:Double,receiptBytes:ByteArray?)->Unit,modifier:Modifier=Modifier,offline:Boolean=false,owner:String="") {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val store=remember(owner,deliveryId){if(owner.isNotBlank())ExpenseDraftStore(context) else null}
    val restored=remember(owner,deliveryId){if(store!=null)runCatching{store.read(owner,deliveryId)}.getOrNull() else null}
    var category by rememberSaveable(deliveryId){mutableStateOf(restored?.category ?: "")}
    var amount by rememberSaveable(deliveryId){mutableStateOf(restored?.amount ?: "")}
    var receipt by remember(deliveryId){mutableStateOf<ByteArray?>(restored?.receipt)}
    var hadReceipt by rememberSaveable(deliveryId){mutableStateOf(restored?.receipt!=null)}
    var receiptError by remember {mutableStateOf<String?>(null)}
    var preparingReceipt by remember {mutableStateOf(false)}

    fun persist(c:String=category,a:String=amount,r:ByteArray?=receipt) {
        if(store!=null) {
            scope.launch(Dispatchers.IO) {
                store.save(owner,deliveryId,ExpenseDraft(category=c,amount=a,receipt=r))
            }
        }
    }

    val picker=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {uri->
        if(uri!=null) {
            receiptError=null;receipt=null
            preparingReceipt=true
            scope.launch {
                try {
                    val bytes=DriverEvidence.readReceiptAsync(context,uri)
                    receipt=bytes;hadReceipt=true
                    persist(r=bytes)
                }catch(cancelled:CancellationException){
                    throw cancelled
                }catch(error:Exception){receiptError=error.message?:"Unable to read the receipt."}
                finally {preparingReceipt=false}
            }
        }
    }
    ModalBottomSheet(onDismissRequest={if(!isSubmitting && !preparingReceipt)onDismiss()},sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),
        containerColor=Color.White,shape=RoundedCornerShape(topStart=24.dp,topEnd=24.dp),modifier=modifier.testTag("add_trip_expense_sheet")) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal=24.dp).padding(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("Add Trip Expense",fontSize=18.sp,fontWeight=FontWeight.Bold,color=DriverNavy,modifier=Modifier.weight(1f))
                Text(trackingNumber,fontSize=10.sp,color=DriverNavy)
                TextButton(onClick=onDismiss,enabled=!isSubmitting && !preparingReceipt){Text("×",fontSize=24.sp)}
            }
            HorizontalDivider(color=Color(0xFFE7EEFF))
            Text("WHAT WAS THE EXPENSE?",fontSize=10.sp)
            OutlinedTextField(category,{category=it;persist(c=it)},placeholder={Text("e.g. Loading fee, parking, etc.")},enabled=!isSubmitting && !preparingReceipt,
                modifier=Modifier.fillMaxWidth().testTag("expense_category_input"),shape=RoundedCornerShape(0.dp),singleLine=true,
                colors=OutlinedTextFieldDefaults.colors(unfocusedContainerColor=Color(0xFFF0F3FF),unfocusedBorderColor=Color(0xFFCDD7F7)))
            Text("AMOUNT",fontSize=10.sp)
            OutlinedTextField(amount,{if(it.isEmpty() || it.matches(Regex("^\\d*\\.?\\d{0,2}$"))){amount=it;persist(a=it)}},placeholder={Text("0.00",fontSize=24.sp)},
                prefix={Text("₱ ",fontSize=24.sp,fontWeight=FontWeight.Bold,color=DriverNavy)},enabled=!isSubmitting && !preparingReceipt,
                modifier=Modifier.fillMaxWidth().testTag("expense_amount_input"),singleLine=true,shape=RoundedCornerShape(0.dp),
                keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),
                colors=OutlinedTextFieldDefaults.colors(unfocusedContainerColor=Color(0xFFF0F3FF),unfocusedBorderColor=Color(0xFFCDD7F7)))
            Text("ATTACH RECEIPT (OPTIONAL)",fontSize=10.sp)
            Surface(onClick={picker.launch("image/*")},enabled=!isSubmitting && !preparingReceipt,color=Color(0xFFF0F3FF),border=BorderStroke(1.dp,Color(0xFFC3C6CF)),modifier=Modifier.testTag("receipt_photo_selector")) {
                Column(Modifier.fillMaxWidth().heightIn(min=150.dp).padding(16.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                    DriverAsset(R.drawable.figma_driver_ceb65,48,46)
                    Text(if(preparingReceipt)"Preparing receipt…" else if(receipt!=null)"Receipt attached · choose another" else "Upload receipt photo",fontSize=14.sp,color=DriverNavy)
                    Text("JPG or PNG · Max 5MB",fontSize=12.sp,color=Color(0xFF73777F))
                }
            }
            if(preparingReceipt)LinearProgressIndicator(Modifier.fillMaxWidth())
            if(receipt!=null)TextButton(onClick={receipt=null;hadReceipt=false;persist(r=null)},enabled=!isSubmitting && !preparingReceipt){Text("Remove receipt")}
            if(hadReceipt && receipt==null && !preparingReceipt)Text("Receipt was not restored. Choose the same receipt again before retrying this expense.",color=MaterialTheme.colorScheme.error)
            if(receiptError!=null)TextButton(onClick={receiptError=null;hadReceipt=false;persist(r=null)}){Text("Continue without receipt")}
            if(offline)Text("Reconnect and refresh before submitting an expense.")
            (receiptError?:errorMessage)?.let {Text(it,color=MaterialTheme.colorScheme.error,fontSize=12.sp)}
            HorizontalDivider(color=Color(0xFFE7EEFF))
            DriverButton(if(isSubmitting)"Submitting…" else "Submit Expense",!isSubmitting && !preparingReceipt && !offline && receiptError==null && (!hadReceipt || receipt!=null) && category.trim().length in 2..200 && amount.toDoubleOrNull()?.let{it.isFinite() && it>0 && it<=1000000}==true) {
                onSubmit(category.trim(),amount.toDouble(),receipt)
            }
            Text("Submitted expenses are available for office review.",fontSize=10.sp,color=Color(0xFF73777F))
        }
    }
}
