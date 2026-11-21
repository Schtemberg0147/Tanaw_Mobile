package com.tanaw.app.feature.workspace.tracking
import android.location.Location
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class TrackingQueueTest {
 @Test fun durableSamplesKeepRetryIdsAndAreIsolatedByAccountAndDelivery() {
  val context=InstrumentationRegistry.getInstrumentation().targetContext
  val database="queue-test-${UUID.randomUUID()}.db"
  val fix=Location("fixture").apply{latitude=0.0;longitude=0.0;accuracy=10f;time=System.currentTimeMillis()}
  var sample:String
  TrackingQueue(context,database).use {queue->
   queue.add("customer-a","trip-a",fix);sample=queue.pending("customer-a","trip-a").single().first
   assertTrue(queue.pending("customer-b","trip-a").isEmpty())
   queue.add("customer-b","trip-b",fix)
  }
  TrackingQueue(context,database).use {queue->
   assertEquals(sample,queue.pending("customer-a","trip-a").single().first)
   queue.retain("customer-a","trip-a")
   assertTrue(queue.pending("customer-b","trip-b").isEmpty())
   queue.clearOwner("customer-a");assertTrue(queue.pending("customer-a","trip-a").isEmpty())
  }
  context.deleteDatabase(database)
 }

 @Test fun queueEvictsOldestSamplesWhenDeliveryExceedsMaxCap() {
  val context=InstrumentationRegistry.getInstrumentation().targetContext
  val database="queue-cap-test-${UUID.randomUUID()}.db"
  val baseTime=System.currentTimeMillis()
  TrackingQueue(context,database,maxSamplesPerDelivery=3).use {queue->
   for(i in 1..5) {
    val fix=Location("fixture").apply{latitude=i.toDouble();longitude=i.toDouble();accuracy=10f;time=baseTime+i*1000L}
    queue.add("driver-a","trip-cap",fix)
   }
   val pending=queue.pending("driver-a","trip-cap")
   assertEquals(3,pending.size)
   assertEquals(listOf(baseTime+3000L,baseTime+4000L,baseTime+5000L),pending.map { it.third })
  }
  context.deleteDatabase(database)
 }
}
