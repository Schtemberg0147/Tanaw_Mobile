package com.tanaw.app.feature.workspace

import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class IncidentRequestTest {
    private val request=UUID.randomUUID().toString()
    @Test fun optionalPhotosAndAllIssueTypesValidate() {
        for(category in IncidentRequest.categories)IncidentRequest.validate(request,category," Traffic delay ",0,emptyList())
        IncidentRequest.validate(request,"traffic","Traffic delay",10080,List(3){byteArrayOf(1)})
    }
    @Test fun invalidInputsAreRejectedBeforeUpload() {
        for(call in listOf<()->Unit>(
            {IncidentRequest.validate("bad","traffic","Traffic delay",0,emptyList())},
            {IncidentRequest.validate(request,"invalid","Traffic delay",0,emptyList())},
            {IncidentRequest.validate(request,"traffic","  ",0,emptyList())},
            {IncidentRequest.validate(request,"traffic","x".repeat(2001),0,emptyList())},
            {IncidentRequest.validate(request,"traffic","Traffic delay",-1,emptyList())},
            {IncidentRequest.validate(request,"traffic","Traffic delay",10081,emptyList())},
            {IncidentRequest.validate(request,"traffic","Traffic delay",0,List(4){byteArrayOf(1)})},
            {IncidentRequest.validate(request,"traffic","Traffic delay",0,listOf(byteArrayOf()))},
            {IncidentRequest.validate(request,"traffic","Traffic delay",0,listOf(ByteArray(5*1024*1024+1)))}
        ))assertTrue(runCatching(call).isFailure)
    }
}
