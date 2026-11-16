package com.tanaw.app.feature.workspace.driver.components

import android.content.res.Configuration
import android.graphics.Bitmap
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanaw.app.R
import com.tanaw.app.feature.workspace.driver.rules.ProofImage
import com.tanaw.app.feature.workspace.driver.screens.DriverAsset
import com.tanaw.app.feature.workspace.driver.screens.DriverButton
import com.tanaw.app.feature.workspace.driver.screens.DriverTheme
import com.tanaw.app.feature.workspace.driver.store.ExpenseDraft
import com.tanaw.app.feature.workspace.driver.store.ExpenseDraftStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
            receiptError=null
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
    val isCategoryError = category.isNotEmpty() && category.trim().length !in 2..200
    val amountVal = amount.toDoubleOrNull()
    val isAmountError = amount.isNotEmpty() && (amountVal == null || !amountVal.isFinite() || amountVal <= 0 || amountVal > 1000000)

    ModalBottomSheet(onDismissRequest={if(!isSubmitting && !preparingReceipt)onDismiss()},sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),
        containerColor=MaterialTheme.colorScheme.surface,shape=RoundedCornerShape(topStart=24.dp,topEnd=24.dp),modifier=modifier.testTag("add_trip_expense_sheet")) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal=24.dp).padding(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("Add Trip Expense",fontSize=18.sp,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onSurface,modifier=Modifier.weight(1f))
                Text(trackingNumber,fontSize=10.sp,color=MaterialTheme.colorScheme.primary)
                TextButton(onClick=onDismiss,enabled=!isSubmitting && !preparingReceipt){Text("×",fontSize=24.sp)}
            }
            HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
            Text("WHAT WAS THE EXPENSE?",fontSize=10.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,fontWeight=FontWeight.SemiBold)
            OutlinedTextField(
                value = category,
                onValueChange = { category = it; persist(c = it) },
                placeholder = { Text("e.g. Loading fee, parking, etc.") },
                enabled = !isSubmitting && !preparingReceipt,
                isError = isCategoryError,
                supportingText = if (isCategoryError) {
                    { Text("Category must be between 2 and 200 characters.") }
                } else null,
                modifier = Modifier.fillMaxWidth().testTag("expense_category_input"),
                shape = RoundedCornerShape(4.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedBorderColor = MaterialTheme.colorScheme.primary
                )
            )
            Text("AMOUNT",fontSize=10.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,fontWeight=FontWeight.SemiBold)
            OutlinedTextField(
                value = amount,
                onValueChange = { if (it.isEmpty() || it.matches(Regex("^\\d*\\.?\\d{0,2}$"))) { amount = it; persist(a = it) } },
                placeholder = { Text("0.00", fontSize = 24.sp) },
                prefix = { Text("₱ ", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
                enabled = !isSubmitting && !preparingReceipt,
                isError = isAmountError,
                supportingText = if (isAmountError) {
                    { Text("Amount must be between ₱0.01 and ₱1,000,000.00.") }
                } else null,
                modifier = Modifier.fillMaxWidth().testTag("expense_amount_input"),
                singleLine = true,
                shape = RoundedCornerShape(4.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedBorderColor = MaterialTheme.colorScheme.primary
                )
            )
            Text("ATTACH RECEIPT (OPTIONAL)",fontSize=10.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,fontWeight=FontWeight.SemiBold)
            ExpenseReceiptSelector(
                receipt = receipt,
                preparingReceipt = preparingReceipt,
                enabled = !isSubmitting && !preparingReceipt,
                onSelectClick = { picker.launch("image/*") }
            )
            if(preparingReceipt)LinearProgressIndicator(Modifier.fillMaxWidth())
            if(receipt!=null)TextButton(onClick={receipt=null;hadReceipt=false;persist(r=null)},enabled=!isSubmitting && !preparingReceipt){Text("Remove receipt")}
            if(hadReceipt && receipt==null && !preparingReceipt)Text("Receipt was not restored. Choose the same receipt again before retrying this expense.",color=MaterialTheme.colorScheme.error)
            if(receiptError!=null)TextButton(onClick={receipt=null;receiptError=null;hadReceipt=false;persist(r=null)}){Text("Continue without receipt")}
            if(offline)Text("Reconnect and refresh before submitting an expense.")
            (receiptError?:errorMessage)?.let {Text(it,color=MaterialTheme.colorScheme.error,fontSize=12.sp)}
            HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
            DriverButton(if(isSubmitting)"Submitting…" else "Submit Expense",!isSubmitting && !preparingReceipt && !offline && receiptError==null && (!hadReceipt || receipt!=null) && category.trim().length in 2..200 && amount.toDoubleOrNull()?.let{it.isFinite() && it>0 && it<=1000000}==true) {
                onSubmit(category.trim(),amount.toDouble(),receipt)
            }
            Text("Submitted expenses are available for office review.",fontSize=10.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun ExpenseReceiptSelector(
    receipt: ByteArray?,
    preparingReceipt: Boolean,
    enabled: Boolean,
    onSelectClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var preview by remember(receipt) { mutableStateOf<Bitmap?>(null) }
    var decodeFailed by remember(receipt) { mutableStateOf(false) }

    LaunchedEffect(receipt) {
        if (receipt == null) {
            preview = null
            decodeFailed = false
        } else {
            val bitmap = withContext(Dispatchers.Default) {
                runCatching { ProofImage.decode(receipt, DriverEvidence.MAX_BYTES) }.getOrNull()
            }
            if (isActive) {
                preview?.recycle()
                preview = bitmap
                decodeFailed = (bitmap == null)
            } else {
                bitmap?.recycle()
            }
        }
    }

    DisposableEffect(receipt) {
        onDispose {
            preview?.recycle()
            preview = null
        }
    }

    Surface(
        onClick = onSelectClick,
        enabled = enabled,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.testTag("receipt_photo_selector")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 150.dp)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
        ) {
            val receiptBitmap = preview
            if (receiptBitmap != null) {
                Image(
                    bitmap = receiptBitmap.asImageBitmap(),
                    contentDescription = "Attached receipt photo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .testTag("receipt_photo_preview")
                )
            } else {
                DriverAsset(R.drawable.figma_driver_ceb65, 48, 46)
                if (decodeFailed) {
                    Text(
                        text = "Unable to display receipt preview",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp
                    )
                }
            }
            Text(
                text = if (preparingReceipt) "Preparing receipt…" else if (receipt != null) "Receipt attached · choose another" else "Upload receipt photo",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "JPG or PNG · Max 5MB",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Preview(showBackground = true, name = "Receipt Selector Empty Light")
@Composable
private fun ExpenseReceiptSelectorEmptyPreview() {
    DriverTheme(darkTheme = false) {
        Box(Modifier.padding(16.dp)) {
            ExpenseReceiptSelector(
                receipt = null,
                preparingReceipt = false,
                enabled = true,
                onSelectClick = {}
            )
        }
    }
}

@Preview(showBackground = true, name = "Receipt Selector Empty Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ExpenseReceiptSelectorEmptyDarkPreview() {
    DriverTheme(darkTheme = true) {
        Box(Modifier.padding(16.dp)) {
            ExpenseReceiptSelector(
                receipt = null,
                preparingReceipt = false,
                enabled = true,
                onSelectClick = {}
            )
        }
    }
}
