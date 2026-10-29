package com.tanaw.app.feature.workspace

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.*
import androidx.core.view.doOnLayout
import androidx.core.graphics.createBitmap
import kotlin.time.Duration.Companion.milliseconds
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
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

private fun pinIcon(context:android.content.Context,label:String):android.graphics.Bitmap {
 val scale=context.resources.displayMetrics.density
 val bitmap=createBitmap((32*scale).toInt(),(42*scale).toInt())
 val canvas=android.graphics.Canvas(bitmap);canvas.scale(scale,scale)
 val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
 paint.color=if(label=="Destination")android.graphics.Color.rgb(37,99,235) else android.graphics.Color.rgb(220,38,38)
 val path=android.graphics.Path().apply {moveTo(16f,41f);lineTo(3f,22f);cubicTo(-9f,-6f,41f,-6f,29f,22f);close()}
 canvas.drawPath(path,paint)
 paint.color=android.graphics.Color.WHITE;canvas.drawCircle(16f,14f,7f,paint)
 return bitmap
}

@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
@Composable fun ShipmentMap(points:List<Pair<String,LatLng>>,geometry:String?=null,onSelect:((LatLng)->Unit)?=null,styleJson:String?=null) {
 val context=LocalContext.current;val lifecycle=LocalLifecycleOwner.current.lifecycle
 val callback by rememberUpdatedState(onSelect)
 val currentPoints by rememberUpdatedState(points)
 val currentGeometry by rememberUpdatedState(geometry)
 val pinIcons=remember(context) {mapOf("Pickup" to pinIcon(context,"Pickup"),"Destination" to pinIcon(context,"Destination"))}
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
    put("features",buildJsonArray {locations.forEach {(label,point)->add(buildJsonObject {
     put("type","Feature")
     put("properties",buildJsonObject {put("icon","shipment-pin-${if(label=="Destination")"Destination" else "Pickup"}")})
     put("geometry",buildJsonObject {put("type","Point");put("coordinates",buildJsonArray {add(point.longitude);add(point.latitude)})})
    })}})
   }.toString()
   style.getSourceAs<GeoJsonSource>("shipment-stops")?.setGeoJson(pins) ?: style.addSource(GeoJsonSource("shipment-stops",pins))
   if(style.getLayer("shipment-stop-pins")==null)style.addLayer(SymbolLayer("shipment-stop-pins","shipment-stops").withProperties(iconImage(Expression.get("icon")),iconAnchor("bottom"),iconAllowOverlap(true),iconIgnorePlacement(true)))
   val moved=signature.lastOrNull{it !in old.orEmpty()}
   view.doOnLayout {
    if(!attached[0])return@doOnLayout
    if(callback==null && locations.map{it.second}.distinct().size>=2) {
     val bounds=org.maplibre.android.geometry.LatLngBounds.Builder().includes(locations.map{it.second}).build()
     map.moveCamera(org.maplibre.android.camera.CameraUpdateFactory.newLatLngBounds(bounds,50))
    } else if(moved!=null) map.cameraPosition=CameraPosition.Builder(map.cameraPosition).target(LatLng(moved.second,moved.third)).zoom(map.cameraPosition.zoom.coerceAtLeast(12.0)).build()
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
 AndroidView(factory={view},modifier=Modifier.fillMaxWidth().height(300.dp).testTag("Shipment map"),update={mapView ->
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
 if(failed)Text("Map unavailable. Check your connection and reopen this step.",color=MaterialTheme.colorScheme.error)
 else if(!ready)SupportingText("Loading street map…")
 else if(onSelect!=null)SupportingText("Map ready · drag to move, pinch to zoom, tap to place a pin.")
 else SupportingText(routeError ?: if(previousGeometry!=null)"Route line displayed · ${points.size} saved stops" else "Saved stops displayed · no road route loaded")
 if(fallback)SupportingText("© OpenFreeMap · © OpenMapTiles · © OpenStreetMap contributors")
}
/** Inline route editor: tapping places a pin immediately; coordinates stay internal. */
@Composable fun BookingRouteMap(pickup:String,destination:String,pickupPoint:LatLng?,destinationPoint:LatLng?,enabled:Boolean=true,
 onPickupText:(String)->Unit,onDestinationText:(String)->Unit,onPickup:(SelectedAddress)->Unit,onDestination:(SelectedAddress)->Unit,
 routeLoader:(suspend (LatLng,LatLng)->RoutePreview)?=null,addressLoader:(suspend (LatLng)->String?)?=null) {
 val context=LocalContext.current
 val pickupCallback by rememberUpdatedState(onPickup);val destinationCallback by rememberUpdatedState(onDestination)
 val currentPickup = rememberUpdatedState(pickup);val currentDestination = rememberUpdatedState(destination)
 var lookupMessage by remember {mutableStateOf<String?>(null)}
 var route by remember {mutableStateOf<RoutePreview?>(null)}
 var routing by remember {mutableStateOf(false)}
 var routeError by remember {mutableStateOf<String?>(null)}
 var retry by remember {mutableIntStateOf(0)}
 LaunchedEffect(pickupPoint?.latitude,pickupPoint?.longitude) {
  val point=pickupPoint ?: return@LaunchedEffect
  val original=currentPickup.value
  if(original!="Pickup map pin") return@LaunchedEffect
  val name=addressLoader?.invoke(point) ?: MobileGeocoding.reverse(context,point)
  if(currentPickup.value==original) {
   if(!name.isNullOrBlank())pickupCallback(SelectedAddress(name,point.latitude,point.longitude))
   else lookupMessage="Address lookup unavailable. The pin is saved; enter a street or landmark."
  }
 }
 LaunchedEffect(destinationPoint?.latitude,destinationPoint?.longitude) {
  val point=destinationPoint ?: return@LaunchedEffect
  val original=currentDestination.value
  if(original!="Destination map pin") return@LaunchedEffect
  val name=addressLoader?.invoke(point) ?: MobileGeocoding.reverse(context,point)
  if(currentDestination.value==original) {
   if(!name.isNullOrBlank())destinationCallback(SelectedAddress(name,point.latitude,point.longitude))
   else lookupMessage="Address lookup unavailable. The pin is saved; enter a street or landmark."
  }
 }
 LaunchedEffect(pickupPoint?.latitude,pickupPoint?.longitude,destinationPoint?.latitude,destinationPoint?.longitude,retry) {
  route=null;routeError=null;routing=false
  if(pickupPoint!=null && destinationPoint!=null && routeLoader!=null) {
   routing=true
   try {kotlinx.coroutines.delay(450.milliseconds);route=routeLoader(pickupPoint,destinationPoint)}
   catch(cancelled:kotlinx.coroutines.CancellationException) {throw cancelled}
   catch(_:Exception) {routeError="Road route unavailable. Retry or adjust the pins; the office will verify the route."}
   finally {routing=false}
  }
 }
 var mode by androidx.compose.runtime.saveable.rememberSaveable {mutableStateOf("pickup")}
 var searchRequest by remember {mutableStateOf<Pair<String,String>?>(null)}
 var searchAttempt by remember {mutableIntStateOf(0)}
 var results by remember {mutableStateOf(emptyList<SelectedAddress>())}
 var error by remember {mutableStateOf<String?>(null)}
 var busy by remember {mutableStateOf(false)}
 val query=if(mode=="pickup") pickup else destination
 LaunchedEffect(mode,query,searchRequest,searchAttempt) {
  results=emptyList();error=null;busy=false
  val request=searchRequest
  if(request!=null && request.first==mode && request.second==query) {
   busy=true
   try {results=MobileGeocoding.search(query);if(results.isEmpty())error="No matches. Try another address or place the pin on the map."}
   catch(cancelled:kotlinx.coroutines.CancellationException) {throw cancelled}
   catch(_:Exception) {error="Address search is unavailable. You can still pan, zoom and tap the map."}
   finally {busy=false}
  }
 }
 OutlinedTextField(pickup,{searchRequest=null;onPickupText(it)},label={Text("Pickup address")},supportingText={Text(if(pickupPoint==null)"Place a pickup pin on the map." else "Pickup pin placed · add a street or landmark if needed.")},enabled=enabled,modifier=Modifier.fillMaxWidth())
 OutlinedTextField(destination,{searchRequest=null;onDestinationText(it)},label={Text("Destination address")},supportingText={Text(if(destinationPoint==null)"Place a destination pin on the map." else "Destination pin placed · add a street or landmark if needed.")},enabled=enabled,modifier=Modifier.fillMaxWidth())
 Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
  FilterChip(selected=mode=="pickup",enabled=enabled,onClick={mode="pickup";searchRequest=null},label={Text("Pin pickup")})
  FilterChip(selected=mode=="destination",enabled=enabled,onClick={mode="destination";searchRequest=null},label={Text("Pin destination")})
 }
 TextButton(enabled=enabled && !busy && query.trim().length>=3,onClick={searchRequest=mode to query;searchAttempt++}) {Text(if(busy)"Searching…" else if(mode=="pickup")"Search pickup address" else "Search destination address")}
 results.forEach {address -> TextButton(enabled=enabled,onClick={
  if(mode=="pickup") onPickup(address) else onDestination(address)
  searchRequest=null
 }) {Text(address.label)} }
 error?.let {SupportingText(it)}
 Text(if(mode=="pickup")"Tap the map to place or move pickup" else "Tap the map to place or move destination",style=MaterialTheme.typography.titleSmall)
 val points=buildList {pickupPoint?.let {add("Pickup" to it)};destinationPoint?.let {add("Destination" to it)}}
 ShipmentMap(points,geometry=route?.geometry,onSelect=if(!enabled)null else {point ->
  lookupMessage=null
  val label=if(mode=="pickup")"Pickup map pin" else "Destination map pin"
  val address=SelectedAddress(label,point.latitude,point.longitude)
  if(mode=="pickup") onPickup(address) else onDestination(address)
  searchRequest=null
 })
 if(routing) {LinearProgressIndicator(Modifier.fillMaxWidth());SupportingText("Calculating road route…")}
 route?.let {SupportingText("Road route · ${it.distanceKm} km · about ${it.minutes} min. Office confirmation required.")}
 routeError?.let {SupportingText(it);TextButton(onClick={retry++},enabled=enabled){Text("Retry route")}}
 lookupMessage?.let {SupportingText(it)}
 SupportingText("Red: pickup · Blue: destination")
 SupportingText("Pickup: ${if(pickupPoint!=null)"pin placed" else "not placed"} · Destination: ${if(destinationPoint!=null)"pin placed" else "not placed"}")
}
