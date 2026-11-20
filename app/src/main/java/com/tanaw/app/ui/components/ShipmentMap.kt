package com.tanaw.app.ui.components

import com.tanaw.app.feature.workspace.model.RoutePreview

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.*
import androidx.core.view.doOnLayout
import androidx.core.graphics.createBitmap
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.tanaw.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapView
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.*
import java.net.URL
import java.net.URLEncoder

data class SelectedAddress(val label:String,val latitude:Double,val longitude:Double)
object MobileGeocoding {
 suspend fun search(query:String):List<SelectedAddress> = withContext(Dispatchers.IO) {
  require(query.trim().length>=3 && BuildConfig.MAPTILER_KEY.isNotBlank())
  val url=URL("https://api.maptiler.com/geocoding/${URLEncoder.encode(query.trim(),"UTF-8")}.json?country=ph&limit=5&key=${URLEncoder.encode(BuildConfig.MAPTILER_KEY,"UTF-8")}")
  val connection=url.openConnection().apply { connectTimeout=10000;readTimeout=10000 }
  val response=connection.getInputStream().bufferedReader().use { it.readText() }
  Json.parseToJsonElement(response).jsonObject["features"]?.jsonArray?.mapNotNull { feature ->
   val record=feature.jsonObject;val center=record["center"]?.jsonArray ?: return@mapNotNull null
   SelectedAddress(record["place_name"]?.jsonPrimitive?.content ?: query,center[1].jsonPrimitive.double,center[0].jsonPrimitive.double)
  }.orEmpty()
 }
 @Suppress("DEPRECATION")
 suspend fun reverse(context:android.content.Context,point:LatLng):String?=withContext(Dispatchers.IO) {
  val provider=runCatching {
   require(BuildConfig.MAPTILER_KEY.isNotBlank())
   val url=URL("https://api.maptiler.com/geocoding/${point.longitude},${point.latitude}.json?language=en&limit=1&key=${URLEncoder.encode(BuildConfig.MAPTILER_KEY,"UTF-8")}")
   val connection=url.openConnection().apply {connectTimeout=6000;readTimeout=6000}
   val data=Json.parseToJsonElement(connection.getInputStream().bufferedReader().use {it.readText()}).jsonObject
   data["features"]?.jsonArray?.firstOrNull()?.jsonObject?.get("place_name")?.jsonPrimitive?.contentOrNull
  }.getOrNull()
  provider?.takeIf {it.isNotBlank()} ?: runCatching {
   if(!android.location.Geocoder.isPresent()) null else android.location.Geocoder(context,java.util.Locale.ENGLISH)
    .getFromLocation(point.latitude,point.longitude,1)?.firstOrNull()?.getAddressLine(0)
  }.getOrNull()
 }

}

private const val STREET_MAP = "https://tiles.openfreemap.org/styles/liberty"

internal fun pinIcon(context:android.content.Context,label:String):android.graphics.Bitmap {
 val scale=context.resources.displayMetrics.density
 val bitmap=createBitmap((32*scale).toInt(),(42*scale).toInt())
 val canvas=android.graphics.Canvas(bitmap);canvas.scale(scale,scale)
 val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
 val isVehicle=label=="Vehicle" || label=="Last recorded vehicle position"
 val isDestination=label=="Destination"
 val markerColor=when {
  isDestination -> android.graphics.Color.rgb(249,115,22) // #F97316 Destination Orange
  isVehicle -> android.graphics.Color.rgb(0,45,84) // #002D54 Deep Navy
  else -> android.graphics.Color.rgb(220,38,38) // #DC2626 Pickup Red
 }
 paint.color=markerColor
 val path=android.graphics.Path().apply {moveTo(16f,41f);lineTo(3f,22f);cubicTo(-9f,-6f,41f,-6f,29f,22f);close()}
 canvas.drawPath(path,paint)
 paint.color=android.graphics.Color.WHITE
 if(isVehicle) {
  canvas.drawCircle(16f,14f,8f,paint)
  paint.color=markerColor
  paint.style=android.graphics.Paint.Style.FILL
  canvas.drawRect(11.5f,10.5f,16.5f,15.5f,paint)
  canvas.drawRect(17f,12f,20.5f,15.5f,paint)
  canvas.drawCircle(13f,16.5f,1.2f,paint)
  canvas.drawCircle(19f,16.5f,1.2f,paint)
 } else {
  canvas.drawCircle(16f,14f,7f,paint)
 }
 return bitmap
}

internal fun topDownTruckIcon(context:android.content.Context):android.graphics.Bitmap {
 val scale=context.resources.displayMetrics.density
 val bitmap=createBitmap((36*scale).toInt(),(52*scale).toInt())
 val canvas=android.graphics.Canvas(bitmap);canvas.scale(scale,scale)
 val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)

 // Soft drop shadow beneath the truck
 paint.style=android.graphics.Paint.Style.FILL
 paint.color=android.graphics.Color.argb(90,0,45,84)
 canvas.drawRoundRect(android.graphics.RectF(7.5f,4.5f,29.5f,48.5f),5.5f,5.5f,paint)

 // Wheels peeking on the sides
 paint.color=android.graphics.Color.rgb(15,23,42)
 canvas.drawRoundRect(android.graphics.RectF(7.5f,10f,10.5f,16f),1.2f,1.2f,paint)
 canvas.drawRoundRect(android.graphics.RectF(25.5f,10f,28.5f,16f),1.2f,1.2f,paint)
 canvas.drawRoundRect(android.graphics.RectF(7.2f,34f,10.2f,41.5f),1.2f,1.2f,paint)
 canvas.drawRoundRect(android.graphics.RectF(25.8f,34f,28.8f,41.5f),1.2f,1.2f,paint)

 // Navy-accented side mirrors
 paint.color=android.graphics.Color.rgb(0,45,84)
 canvas.drawRoundRect(android.graphics.RectF(6f,12.8f,9.4f,15.2f),1f,1f,paint)
 canvas.drawRoundRect(android.graphics.RectF(26.6f,12.8f,30f,15.2f),1f,1f,paint)

 // Cab chassis (front points North / top for 0-degree rotation)
 val cabRect=android.graphics.RectF(10f,5f,26f,18.2f)
 paint.color=android.graphics.Color.rgb(248,250,252)
 canvas.drawRoundRect(cabRect,4.5f,4.5f,paint)
 paint.style=android.graphics.Paint.Style.STROKE
 paint.strokeWidth=1.2f
 paint.color=android.graphics.Color.rgb(0,45,84)
 canvas.drawRoundRect(cabRect,4.5f,4.5f,paint)

 // Front LED headlights (Electric Blue)
 paint.style=android.graphics.Paint.Style.FILL
 paint.color=android.graphics.Color.rgb(3,131,213)
 canvas.drawRoundRect(android.graphics.RectF(11.6f,5.4f,14.6f,6.9f),0.8f,0.8f,paint)
 canvas.drawRoundRect(android.graphics.RectF(21.4f,5.4f,24.4f,6.9f),0.8f,0.8f,paint)

 // Panoramic dark navy windshield with electric blue highlight line
 paint.color=android.graphics.Color.rgb(0,45,84)
 canvas.drawRoundRect(android.graphics.RectF(11.4f,8.2f,24.6f,13.6f),2.4f,2.4f,paint)
 paint.style=android.graphics.Paint.Style.STROKE
 paint.strokeWidth=0.9f
 paint.color=android.graphics.Color.argb(200,3,131,213)
 canvas.drawLine(12.6f,9.4f,23.4f,9.4f,paint)

 // Cab roof accent vent
 paint.style=android.graphics.Paint.Style.FILL
 paint.color=android.graphics.Color.rgb(3,131,213)
 canvas.drawRoundRect(android.graphics.RectF(15f,14.8f,21f,16.7f),0.9f,0.9f,paint)

 // Cargo box (rear)
 val cargoRect=android.graphics.RectF(9f,19f,27f,46.2f)
 paint.color=android.graphics.Color.WHITE
 canvas.drawRoundRect(cargoRect,3.2f,3.2f,paint)
 paint.style=android.graphics.Paint.Style.STROKE
 paint.strokeWidth=1.2f
 paint.color=android.graphics.Color.rgb(0,45,84)
 canvas.drawRoundRect(cargoRect,3.2f,3.2f,paint)

 // Navy cargo header band & subtle roof ribs
 paint.style=android.graphics.Paint.Style.FILL
 paint.color=android.graphics.Color.rgb(0,45,84)
 canvas.drawRoundRect(android.graphics.RectF(9.8f,19.8f,26.2f,23.4f),1.4f,1.4f,paint)

 paint.style=android.graphics.Paint.Style.STROKE
 paint.strokeWidth=0.9f
 paint.color=android.graphics.Color.rgb(203,213,225)
 canvas.drawLine(13.5f,25.6f,13.5f,43.2f,paint)
 canvas.drawLine(18f,25.6f,18f,43.2f,paint)
 canvas.drawLine(22.5f,25.6f,22.5f,43.2f,paint)

 // Rear orange accent indicator bar
 paint.style=android.graphics.Paint.Style.FILL
 paint.color=android.graphics.Color.rgb(249,115,22)
 canvas.drawRoundRect(android.graphics.RectF(12.5f,44.3f,23.5f,45.4f),0.6f,0.6f,paint)

 return bitmap
}

@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
@Composable fun ShipmentMap(
 points:List<Pair<String,LatLng>>,
 geometry:String?=null,
 onSelect:((LatLng)->Unit)?=null,
 styleJson:String?=null,
 modifier:Modifier=Modifier.fillMaxWidth().height(300.dp),
 showCaptionText:Boolean=true,
 recenterTrigger:Int=0,
 boundsPaddingPx:Int=50,
 boundsPaddingTopPx:Int=50,
 vehicleHeadingDeg:Float?=null
) {
 val context=LocalContext.current;val lifecycle=LocalLifecycleOwner.current.lifecycle
 val callback by rememberUpdatedState(onSelect)
 val currentPoints by rememberUpdatedState(points)
 val currentGeometry by rememberUpdatedState(geometry)
 val currentHeading by rememberUpdatedState(vehicleHeadingDeg)
 val pinIcons=remember(context,vehicleHeadingDeg!=null) {
  mapOf(
   "Pickup" to pinIcon(context,"Pickup"),
   "Destination" to pinIcon(context,"Destination"),
   "Vehicle" to if(vehicleHeadingDeg!=null) topDownTruckIcon(context) else pinIcon(context,"Vehicle")
  )
 }
 var fallback by remember {mutableStateOf(BuildConfig.MAPTILER_KEY.isBlank())}
 var ready by remember {mutableStateOf(false)}
 var failed by remember {mutableStateOf(false)}
 var requested by remember {mutableStateOf(false)}
 var routeError by remember {mutableStateOf<String?>(null)}
 var previous by remember {mutableStateOf<List<Triple<String,Double,Double>>?>(null)}
 var previousHeading by remember {mutableStateOf<Float?>(null)}
 var previousGeometry by remember {mutableStateOf<String?>(null)}
 val view=remember {MapLibre.getInstance(context);MapView(context).apply {onCreate(null)}}
 val attached=remember {booleanArrayOf(true)}
 fun render(map:org.maplibre.android.maps.MapLibreMap,style:org.maplibre.android.maps.Style) {
  val locations=currentPoints
  val signature=locations.map {(label,point)->Triple(label,point.latitude,point.longitude)}
  val heading=currentHeading
  val road=currentGeometry?.takeUnless{it.isBlank() || it=="null"}
  if(previous!=signature || previousHeading!=heading) {
   val old=previous
   pinIcons.forEach {(label,bitmap)->style.addImage("shipment-pin-$label",bitmap)}
   val isVehicleLabel:(String)->Boolean={it=="Vehicle" || it=="Last recorded vehicle position"}
   val stopPins=buildJsonObject {
    put("type","FeatureCollection")
    put("features",buildJsonArray {locations.filterNot{isVehicleLabel(it.first)}.forEach {(label,point)->
     val iconName=if(label=="Destination") "Destination" else "Pickup"
     add(buildJsonObject {
      put("type","Feature")
      put("properties",buildJsonObject {put("icon","shipment-pin-$iconName")})
      put("geometry",buildJsonObject {put("type","Point");put("coordinates",buildJsonArray {add(point.longitude);add(point.latitude)})})
     })
    }})
   }.toString()
   style.getSourceAs<GeoJsonSource>("shipment-stops")?.setGeoJson(stopPins) ?: style.addSource(GeoJsonSource("shipment-stops",stopPins))
   if(style.getLayer("shipment-stop-pins")==null) {
    style.addLayer(
     SymbolLayer("shipment-stop-pins","shipment-stops").withProperties(
      iconImage(Expression.get("icon")),
      iconAnchor(Property.ICON_ANCHOR_BOTTOM),
      iconAllowOverlap(true),
      iconIgnorePlacement(true)
     )
    )
   }
   val vehiclePins=buildJsonObject {
    put("type","FeatureCollection")
    put("features",buildJsonArray {locations.filter{isVehicleLabel(it.first)}.forEach {(_,point)->
     add(buildJsonObject {
      put("type","Feature")
      put("properties",buildJsonObject {put("bearing",(heading ?: 0f).toDouble())})
      put("geometry",buildJsonObject {put("type","Point");put("coordinates",buildJsonArray {add(point.longitude);add(point.latitude)})})
     })
    }})
   }.toString()
   style.getSourceAs<GeoJsonSource>("shipment-vehicle")?.setGeoJson(vehiclePins) ?: style.addSource(GeoJsonSource("shipment-vehicle",vehiclePins))
   if(style.getLayer("shipment-vehicle-pin")==null) {
    val vehicleProps = if(heading != null) {
     arrayOf(
      iconImage("shipment-pin-Vehicle"),
      iconAnchor(Property.ICON_ANCHOR_CENTER),
      iconRotate(Expression.get("bearing")),
      iconRotationAlignment(Expression.literal(Property.ICON_ROTATION_ALIGNMENT_MAP)),
      iconAllowOverlap(true),
      iconIgnorePlacement(true)
     )
    } else {
     arrayOf(
      iconImage("shipment-pin-Vehicle"),
      iconAnchor(Property.ICON_ANCHOR_BOTTOM),
      iconAllowOverlap(true),
      iconIgnorePlacement(true)
     )
    }
    style.addLayer(SymbolLayer("shipment-vehicle-pin","shipment-vehicle").withProperties(*vehicleProps))
   }
   val stopSignature=signature.filter{!isVehicleLabel(it.first)}
   val oldStopSignature=old?.filter{!isVehicleLabel(it.first)}
   val isInitialOrStopsChanged=old.isNullOrEmpty() || oldStopSignature!=stopSignature
   val moved=signature.lastOrNull{it !in old.orEmpty()}
   view.doOnLayout {
    if(!attached[0])return@doOnLayout
    if(isInitialOrStopsChanged) {
     if(callback==null && locations.map{it.second}.distinct().size>=2) {
      val bounds=org.maplibre.android.geometry.LatLngBounds.Builder().includes(locations.map{it.second}).build()
      map.moveCamera(org.maplibre.android.camera.CameraUpdateFactory.newLatLngBounds(bounds,boundsPaddingPx,boundsPaddingTopPx,boundsPaddingPx,boundsPaddingPx))
     } else if(moved!=null) map.cameraPosition=CameraPosition.Builder(map.cameraPosition).target(LatLng(moved.second,moved.third)).zoom(map.cameraPosition.zoom.coerceAtLeast(12.0)).build()
    }
   }
   previous=signature
   previousHeading=heading
  }
  try {
   if(road==null) {
    style.removeLayer("shipment-route-glow");style.removeLayer("shipment-route-line");style.removeSource("shipment-route");previousGeometry=null;routeError=null
   } else if(previousGeometry!=road || style.getLayer("shipment-route-line")==null) {
    style.getSourceAs<GeoJsonSource>("shipment-route")?.setGeoJson(road)
     ?: style.addSource(GeoJsonSource("shipment-route",road,GeoJsonOptions().withLineMetrics(true)))
    if(style.getLayer("shipment-route-glow")==null) {
     style.addLayerBelow(
      LineLayer("shipment-route-glow","shipment-route").withProperties(
       lineColor("#0383D5"),
       lineWidth(9f),
       lineOpacity(0.20f),
       lineBlur(Expression.literal(3f)),
       lineCap(Property.LINE_CAP_ROUND),
       lineJoin(Expression.literal(Property.LINE_JOIN_ROUND))
      ),
      "shipment-stop-pins"
     )
    }
    if(style.getLayer("shipment-route-line")==null) {
     style.addLayerBelow(
      LineLayer("shipment-route-line","shipment-route").withProperties(
       lineColor("#0383D5"),
       lineWidth(5f),
       lineCap(Property.LINE_CAP_ROUND),
       lineJoin(Expression.literal(Property.LINE_JOIN_ROUND))
      ),
      "shipment-stop-pins"
     )
    }
    previousGeometry=road;routeError=null
   }
  } catch(_:Exception) {routeError="Route line could not be displayed. Refresh the trip and retry."}
 }
 fun loaded(map:org.maplibre.android.maps.MapLibreMap,style:org.maplibre.android.maps.Style) {
  if(!attached[0])return
  // Styles load asynchronously and replace custom layers. Draw using the latest data here,
  // not only in AndroidView.update, which may have run before the style existed.
  previous=null;previousHeading=null;previousGeometry=null;ready=true;failed=false
  render(map,style)
 }
 DisposableEffect(view,lifecycle) {
  attached[0]=true
  view.getMapAsync {map ->
   map.cameraPosition=CameraPosition.Builder().target(LatLng(14.5995,120.9842)).zoom(11.0).build()
   map.addOnMapClickListener {point -> if(ready && !failed)callback?.invoke(point);callback!=null}
  }
  view.addOnDidFailLoadingMapListener { _ ->
   ready=false
   if(!fallback) {
    fallback=true
    view.getMapAsync {map->
     map.setStyle(STREET_MAP){style->loaded(map,style)}
    }
   } else failed=true
  }
  view.setOnTouchListener {_,event ->
   view.parent?.requestDisallowInterceptTouchEvent(event.actionMasked!=android.view.MotionEvent.ACTION_UP && event.actionMasked!=android.view.MotionEvent.ACTION_CANCEL)
   false
  }
  val observer=LifecycleEventObserver { _,event -> when(event) {
   Lifecycle.Event.ON_START->view.onStart();Lifecycle.Event.ON_RESUME->view.onResume();Lifecycle.Event.ON_PAUSE->view.onPause();Lifecycle.Event.ON_STOP->view.onStop();else->Unit
  }}
  lifecycle.addObserver(observer)
  if(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))view.onStart()
  if(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))view.onResume()
  onDispose {attached[0]=false;lifecycle.removeObserver(observer);view.onPause();view.onStop();view.onDestroy()}
 }
 val mapDesc = when {
  points.isEmpty() -> "Interactive shipment map"
  else -> "Interactive shipment map showing " + points.joinToString(", ") { (label, _) -> label }
 }
 AndroidView(factory={view},modifier=modifier.testTag("Shipment map").semantics { contentDescription = mapDesc },update={mapView ->
  mapView.getMapAsync {map ->
   if(!requested) {
    requested=true
    if(styleJson!=null)map.setStyle(org.maplibre.android.maps.Style.Builder().fromJson(styleJson)){style->loaded(map,style)}
    else {
     val resolvedUrl=if(fallback) {
      STREET_MAP
     } else {
      "https://api.maptiler.com/maps/streets-v2/style.json?key=${BuildConfig.MAPTILER_KEY}"
     }
     map.setStyle(resolvedUrl){style->loaded(map,style)}
    }
   }
   else if(ready)map.getStyle {style->render(map,style)}
  }
 })
 LaunchedEffect(points.map{Triple(it.first,it.second.latitude,it.second.longitude)},geometry,ready,vehicleHeadingDeg) {
  if(ready)view.getMapAsync {map->map.getStyle {style->render(map,style)}}
 }
 LaunchedEffect(recenterTrigger,ready) {
  if(recenterTrigger>0 && ready) {
   view.getMapAsync {map ->
    val locations=currentPoints
    if(locations.map{it.second}.distinct().size>=2) {
     val bounds=org.maplibre.android.geometry.LatLngBounds.Builder().includes(locations.map{it.second}).build()
     map.animateCamera(org.maplibre.android.camera.CameraUpdateFactory.newLatLngBounds(bounds,boundsPaddingPx,boundsPaddingTopPx,boundsPaddingPx,boundsPaddingPx))
    } else if(locations.size==1) {
     map.animateCamera(org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(locations.first().second,14.0))
    }
   }
  }
 }
 if(showCaptionText) {
  if(failed)Text("Map unavailable. Check your connection and reopen this step.",color=MaterialTheme.colorScheme.error)
  else if(!ready)SupportingText("Loading street map…")
  else if(onSelect!=null)SupportingText("Map ready · drag to move, pinch to zoom, tap to place a pin.")
  else SupportingText(routeError ?: if(previousGeometry!=null)"Route line displayed · ${points.size} saved stops" else "Saved stops displayed · no road route loaded")
  if(points.any { it.first == "Vehicle" || it.first == "Last recorded vehicle position" }) SupportingText("Red: pickup · Orange: destination · Navy: last recorded vehicle position")
  if(fallback)SupportingText("© OpenFreeMap · © OpenMapTiles · © OpenStreetMap contributors")
 }
}
