package com.tanaw.app.feature.workspace

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject

/** Reconcile uncertain responses using the immutable request/file identity, never delete on uncertainty. */
internal object ExpenseSubmission {
    fun matches(saved:JsonObject,delivery:String,driver:String,description:String,amount:java.math.BigDecimal,path:String?):Boolean =
        saved.value("delivery_id")==delivery && saved.value("driver_id")==driver &&
            saved.value("description").ifBlank{saved.value("category")}.equals(description.trim(),true) &&
            saved.value("amount").toBigDecimalOrNull()?.compareTo(amount)==0 && saved.value("receipt_path").ifBlank{null}==path
    suspend fun run(find:suspend ()->JsonObject?,verify:suspend (JsonObject)->Unit,
                    upload:suspend ()->Unit,insert:suspend ()->Unit) {
        find()?.let {verify(it);return}
        upload()
        try {insert()}
        catch(cancelled:CancellationException){throw cancelled}
        catch(failure:Exception) {
            val saved=try {find()}catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){throw failure}
            if(saved==null)throw failure
            verify(saved)
        }
    }
    suspend fun upload(bytes:ByteArray,write:suspend ()->Unit,read:suspend ()->ByteArray) {
        try {write()}
        catch(cancelled:CancellationException){throw cancelled}
        catch(failure:Exception) {
            val saved=try {read()}catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){throw failure}
            check(saved.contentEquals(bytes)){"The saved receipt differs. Reopen the form for a new expense."}
        }
    }
}
