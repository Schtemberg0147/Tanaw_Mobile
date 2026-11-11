package com.tanaw.app.feature.workspace

import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

object MobileBookingRules {
 val zone:ZoneId=ZoneId.of("Asia/Manila")
 val format:DateTimeFormatter=DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm").withResolverStyle(ResolverStyle.STRICT)
 fun earliest(now:Instant=Instant.now()):LocalDateTime=now.truncatedTo(java.time.temporal.ChronoUnit.MINUTES).plusSeconds(60).atZone(zone).toLocalDateTime()
 fun departure(raw:String,now:Instant=Instant.now()):String?=runCatching {
  val local=LocalDateTime.parse(raw.trim(),format)
  val instant=local.atZone(zone).toInstant()
  instant.takeIf { !local.isBefore(earliest(now)) && !it.isAfter(now.atZone(zone).plusYears(1).toInstant()) }?.toString()
 }.getOrNull()
 fun fits(weight:String,capacity:String):Boolean {
  val w=MobileValidation.weight(weight) ?: return false
  val c=capacity.toDoubleOrNull()?.takeIf {it.isFinite() && it>0} ?: return false
  return w<=c && c<=w+2000
 }
}
