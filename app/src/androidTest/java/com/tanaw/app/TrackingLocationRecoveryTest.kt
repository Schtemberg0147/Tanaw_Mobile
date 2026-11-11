package com.tanaw.app

import android.location.LocationManager
import android.os.Build
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.tanaw.app.feature.workspace.freshDriverLocation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class TrackingLocationRecoveryTest {
    @Test @SdkSuppress(minSdkVersion=28)
    fun disabledSystemLocationHasActionableRecoveryAndRestoresDeviceSetting() = runBlocking {
        assumeTrue(Build.MODEL.contains("sdk") || Build.FINGERPRINT.contains("generic"))
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val manager=instrumentation.targetContext.getSystemService(LocationManager::class.java)
        val previous=manager.isLocationEnabled
        fun enabled(value:Boolean) {
            instrumentation.uiAutomation.executeShellCommand("cmd location set-location-enabled $value").use { descriptor ->
                android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use {it.readBytes()}
            }
        }
        try {
            enabled(false)
            val result=runCatching {freshDriverLocation(instrumentation.targetContext)}
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("Enable location"))
        } finally {enabled(previous)}
        assertEquals(previous,manager.isLocationEnabled)
    }
}
