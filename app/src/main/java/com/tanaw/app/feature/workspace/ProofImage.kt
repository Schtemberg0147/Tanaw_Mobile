package com.tanaw.app.feature.workspace

import android.graphics.Bitmap
import android.graphics.BitmapFactory

object ProofImage {
    fun decode(bytes: ByteArray,maxBytes:Int=5*1024*1024): Bitmap {
        require(bytes.isNotEmpty() && bytes.size<=maxBytes)
        val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
        BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
        require(bounds.outWidth>0 && bounds.outHeight>0)
        var sample=1
        while(bounds.outWidth/sample>1600 || bounds.outHeight/sample>1600) sample*=2
        return BitmapFactory.decodeByteArray(bytes,0,bytes.size,BitmapFactory.Options().apply { inSampleSize=sample })
            ?: error("INVALID_PROOF_IMAGE")
    }
}
