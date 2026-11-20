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
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.layers.LineLayer
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
  isDestination -> android.graphics.Color.rgb(37,99,235)
  isVehicle -> android.graphics.Color.rgb(22,163,74)
  else -> android.graphics.Color.rgb(220,38,38)
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
 boundsPaddingTopPx:Int=50
) {
 val context=LocalContext.current;val lifecycle=LocalLifecycleOwner.current.lifecycle
 val callback by rememberUpdatedState(onSelect)
 val currentPoints by rememberUpdatedState(points)
 val currentGeometry by rememberUpdatedState(geometry)
 val pinIcons=remember(context) {mapOf("Pickup" to pinIcon(context,"Pickup"),"Destination" to pinIcon(context,"Destination"),"Vehicle" to pinIcon(context,"Vehicle"))}
 var fallback by remember {mutableStateOf(BuildConfig.MAPTILER_KEY.isBlank())}
 var ready by remember {mutableStateOf(false)}
 var failed by remember {mutableStateOf(false)}
 var requested by remember {mutableStateOf(false)}
 var routeError by remember {mutableStateOf<String?>(null)}
 var previous by remember {mutableStateOf<List<Triple<String,Double,Double>>?>(null)}
 var previousGeometry by remember {mutableStateOf<String?>(null)}
 val view=remember {MapLibre.getInstance(context);MapView(context).apply {onCreate(null)}}
 val attached=remember {booleanArrayOf(true)}
 fun render(map:org.maplibre.android.maps.MapLibreMap,style:org.maplibre.android.maps.Style) {
  val locations=currentPoints
  val signature=locations.map {(label,point)->Triple(label,point.latitude,point.longitude)}
  val road=currentGeometry?.takeUnless{it.isBlank() || it=="null"}
  if(previous!=signature) {
   val old=previous
   pinIcons.forEach {(label,bitmap)->style.addImage("shipment-pin-$label",bitmap)}
   val pins=buildJsonObject {
    put("type","FeatureCollection")
    put("features",buildJsonArray {locations.forEach {(label,point)->
     val iconName=when {
      label=="Destination" -> "Destination"
      label=="Vehicle" || label=="Last recorded vehicle position" -> "Vehicle"
      else -> "Pickup"
     }
     add(buildJsonObject {
      put("type","Feature")
      put("properties",buildJsonObject {put("icon","shipment-pin-$iconName")})
      put("geometry",buildJsonObject {put("type","Point");put("coordinates",buildJsonArray {add(point.longitude);add(point.latitude)})})
     })
    }})
   }.toString()
   style.getSourceAs<GeoJsonSource>("shipment-stops")?.setGeoJson(pins) ?: style.addSource(GeoJsonSource("shipment-stops",pins))
   if(style.getLayer("shipment-stop-pins")==null)style.addLayer(SymbolLayer("shipment-stop-pins","shipment-stops").withProperties(iconImage(Expression.get("icon")),iconAnchor("bottom"),iconAllowOverlap(true),iconIgnorePlacement(true)))
   val isVehicleLabel:(String)->Boolean={it=="Vehicle" || it=="Last recorded vehicle position"}
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
  }
  try {
   if(road==null) {
    style.removeLayer("shipment-route-line");style.removeSource("shipment-route");previousGeometry=null;routeError=null
   } else if(previousGeometry!=road || style.getLayer("shipment-route-line")==null) {
    style.getSourceAs<GeoJsonSource>("shipment-route")?.setGeoJson(road) ?: style.addSource(GeoJsonSource("shipment-route",road))
    if(style.getLayer("shipment-route-line")==null)style.addLayerBelow(LineLayer("shipment-route-line","shipment-route").withProperties(lineColor("#2563eb"),lineWidth(5f)),"shipment-stop-pins")
    previousGeometry=road;routeError=null
   }
  } catch(_:Exception) {routeError="Route line could not be displayed. Refresh the trip and retry."}
 }
 fun loaded(map:org.maplibre.android.maps.MapLibreMap,style:org.maplibre.android.maps.Style) {
  if(!attached[0])return
  // Styles load asynchronously and replace custom layers. Draw using the latest data here,
  // not only in AndroidView.update, which may have run before the style existed.
  previous=null;previousGeometry=null;ready=true;failed=false
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
   if(!fallback) {fallback=true;view.getMapAsync {map->map.setStyle(STREET_MAP){style->loaded(map,style)}}} else failed=true
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
    else map.setStyle(if(fallback)STREET_MAP else "https://api.maptiler.com/maps/streets-v2/style.json?key=${BuildConfig.MAPTILER_KEY}"){style->loaded(map,style)}
   }
   else if(ready)map.getStyle {style->render(map,style)}
  }
 })
 LaunchedEffect(points.map{Triple(it.first,it.second.latitude,it.second.longitude)},geometry,ready) {
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
  if(points.any { it.first == "Vehicle" || it.first == "Last recorded vehicle position" }) SupportingText("Red: pickup · Blue: destination · Green: last recorded vehicle position")
  if(fallback)SupportingText("© OpenFreeMap · © OpenMapTiles · © OpenStreetMap contributors")
 }
}
