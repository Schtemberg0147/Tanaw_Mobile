package com.tanaw.app.feature.workspace

import java.util.UUID

internal object IncidentRequest {
    val categories=setOf("traffic","breakdown","accident","weather","other")
    fun validate(request:String,category:String,description:String,minutes:Int,photos:List<ByteArray>) {
        UUID.fromString(request)
        require(category in categories && description.trim().length in 5..2000 && minutes in 0..10080) {"Check the issue type, description and expected delay."}
        require(photos.size<=3 && photos.all {it.size in 1..(5*1024*1024)}) {"Attach up to three photos, each no larger than 5 MB."}
    }
}
