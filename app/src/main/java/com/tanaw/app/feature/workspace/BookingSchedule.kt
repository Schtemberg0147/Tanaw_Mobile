package com.tanaw.app.feature.workspace

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.time.*

/** Only future Philippine departure times appear in the hour/minute menus. */
@Composable fun BookingSchedule(value:String,enabled:Boolean,onChange:(String)->Unit) {
 val context=LocalContext.current
 var date by remember {mutableStateOf<LocalDate?>(null)}
 var hour by remember {mutableIntStateOf(0)}
 var minute by remember {mutableIntStateOf(0)}
 var clock by remember {mutableStateOf(Instant.now())}
 LaunchedEffect(Unit) {while(true) {clock=Instant.now();kotlinx.coroutines.delay(1000)}}
 val earliest=MobileBookingRules.earliest(clock)
 OutlinedTextField(value,{},readOnly=true,label={Text("Departure · Philippine time")},isError=value.isNotBlank() && MobileBookingRules.departure(value,clock)==null,
  supportingText={Text(if(value.isNotBlank() && MobileBookingRules.departure(value,clock)==null)"This departure has passed. Choose a new future date and time." else "Past dates and times cannot be selected.")},modifier=Modifier.fillMaxWidth())
 OutlinedButton(enabled=enabled,onClick={
  val current=runCatching {LocalDateTime.parse(value,MobileBookingRules.format)}.getOrNull()?.takeIf {!it.isBefore(earliest)} ?: earliest
  val picker=DatePickerDialog(context,{_,y,m,d ->
   val selected=LocalDate.of(y,m+1,d)
   val minimum=MobileBookingRules.earliest()
   val initial=if(selected==minimum.toLocalDate())minimum else selected.atTime(8,0)
   hour=initial.hour;minute=initial.minute;date=selected
  },current.year,current.monthValue-1,current.dayOfMonth)
  fun millis(day:LocalDate)=day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
  picker.datePicker.minDate=millis(earliest.toLocalDate())
  picker.datePicker.maxDate=millis(clock.atZone(MobileBookingRules.zone).plusYears(1).toLocalDate())
  picker.show()
 },modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {Text("Choose date & time")}
 date?.let {selected ->
  val today=selected==earliest.toLocalDate()
  val hours=(0..23).filter {!today || it>=earliest.hour}
  val minutes=(0..59).filter {!today || hour>earliest.hour || it>=earliest.minute}
  LaunchedEffect(earliest,selected,hour) {
   if(selected<earliest.toLocalDate()) date=null
   else {if(hour !in hours)hour=hours.first();if(minute !in minutes)minute=minutes.first()}
  }
  val candidate=selected.atTime(hour,minute).format(MobileBookingRules.format)
  AlertDialog(onDismissRequest={date=null},title={Text("Departure time")},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
   Text("$selected · Philippine time (UTC+08:00)")
   Row(horizontalArrangement=Arrangement.spacedBy(16.dp)) {
    TimeChoice("Hour",hour,hours) {hour=it}
    TimeChoice("Minute",minute,minutes) {minute=it}
   }
   SupportingText("Only future times are available. Hours use the 24-hour clock.")
  }},confirmButton={TextButton(enabled=enabled && MobileBookingRules.departure(candidate,clock)!=null,onClick={if(MobileBookingRules.departure(candidate)!=null){onChange(candidate);date=null}}){Text("Set departure")}},dismissButton={TextButton(onClick={date=null}){Text("Cancel")}})
 }
}
@Composable private fun TimeChoice(label:String,value:Int,options:List<Int>,select:(Int)->Unit) {
 var expanded by remember {mutableStateOf(false)}
 Box {
  OutlinedButton(onClick={expanded=true}) {Text("$label: ${value.toString().padStart(2,'0')}")}
  DropdownMenu(expanded=expanded,onDismissRequest={expanded=false},modifier=Modifier.heightIn(max=260.dp)) {
   options.forEach {option->DropdownMenuItem(text={Text(option.toString().padStart(2,'0'))},onClick={select(option);expanded=false})}
  }
 }
}
