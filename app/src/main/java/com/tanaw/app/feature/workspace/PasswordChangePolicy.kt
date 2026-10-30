package com.tanaw.app.feature.workspace

import kotlinx.serialization.json.JsonObject

/** A signed-in password change never borrows another account's recovery context or Profile. */
internal object PasswordChangePolicy {
    fun target(phone:String?,email:String?):String? = MobileValidation.phone(phone.orEmpty())
        ?: phone?.trim()?.takeIf{it.matches(Regex("639[0-9]{9}"))}?.let{"+$it"}
        ?: email?.trim()?.takeIf(MobileValidation::email)
    fun begin(state:MobileState,owner:String,phone:String?,email:String?):MobileState {
        val profile=state.data?.obj("profile")
        require(state.page=="workspace" && !state.offline && profile!=null && profile.value("id")==owner && profile.value("role")=="driver")
        return state.copy(passwordChangeOwner=owner,passwordChangeCodeSent=false,email=requireNotNull(target(phone,email)),
            error=null,message=null,resendSeconds=0)
    }
    fun current(state:MobileState,owner:String?):Boolean {
        val profile=state.data?.obj("profile")
        return owner!=null && state.passwordChangeOwner==owner && profile!=null && profile.value("id")==owner && profile.value("role")=="driver"
    }
    fun cancel(state:MobileState,owner:String?):MobileState = if(current(state,owner))
        state.copy(page="workspace",passwordChangeOwner=null,passwordChangeCodeSent=false,email="",resendSeconds=0,error=null,message=null)
        else MobileState(page="login",message="Sign in again to manage your password.")
    fun canRestore(row:JsonObject,owner:String,phone:String?,email:String?):Boolean =
        row.value("page")=="recover_code" && row.value("return_owner")==owner &&
            row.value("target").equals(target(phone,email),ignoreCase=true)
}
