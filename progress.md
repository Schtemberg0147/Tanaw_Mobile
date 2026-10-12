# TANAW Mobile App – Senior Developer Production-Readiness Audit & Progress Report

**Application**: Tanaw Native Android App (Logistics & Freight Shipping for Mabutol Trucking)  
**Tech Stack**: Kotlin, Jetpack Compose, Material 3, Hilt DI, Supabase (Auth, Postgrest DB & RPCs), MapLibre Native SDK, Google Fused Location  
**Target Region**: Luzon, Philippines (SW: 11.8°N, 119.5°E to NE: 18.8°N, 124.5°E)  
**Audit Perspective**: Senior Mobile Architect / Technical Lead Evaluation for Production Launch  
**Last Updated**: February 2025  

---

## 📊 Production-Readiness Scorecard

| Status Metric | Strict Production-Ready | UI Prototype / Mocked | Pending / Not Started | Total Features |
| :--- | :---: | :---: | :---: | :---: |
| **Feature Count** | **18** | **16** | **5** | **39** |
| **Percentage** | **46.2%** | **41.0%** | **12.8%** | **100%** |

> 🚨 **Senior Architect Verdict**:  
> The mobile app has an **excellent UI foundation and solid Authentication pipeline (75%+ UI completeness)**. However, from a **strict production-readiness standpoint**, the app is currently at **~46%**. Crucial backend database persistence for bookings, live telemetry via WebSockets/Supabase Realtime, and background GPS services for drivers are required before the app can be deployed to real users.

---

## 🧐 Technical Audit by Category

### 🟢 1. Authentication & Session Management — **100% Production Ready**
- [x] **Login Flow** (`LoginScreen.kt`, `SupabaseAuthRepositoryImpl.kt`): Full integration with Supabase Auth & role retrieval.
- [x] **Role Routing** (`MainActivity.kt`): Dynamically routes `customer` to Customer Home and `driver` to Driver Home.
- [x] **Create Account & Validation**: Complete with multi-step OTP verification via email and SMS (`VerifyEmailScreen.kt`, `VerifyPhoneScreen.kt`).
- [x] **Session Storage & Restore**: Session tokens managed securely by Supabase Client across app restarts.
- [x] **Profile & Security**: Editing name/phone/email and changing passwords persists directly to Supabase Auth & public user records.

---

### 🟡 2. Booking Pipeline & Location Picker — **50% Production Ready**
- [x] **Location Picker Engine** (`LocationPickerScreen.kt`, `LocationPickerViewModel.kt`): **FULLY FUNCTIONAL**. Integrated with MapLibre Native, restricted to **Luzon camera bounds**, features camera drag selection (`onCameraIdle`), a floating recenter button, and queries `nearby_locations` RPC on Supabase.
- [x] **Cargo & Pricing Engine** (`AddDetailsScreen.kt`): Computes rates based on distance, cargo weight, and special handling (fragile, keep dry, temperature sensitive).
- [!] **CRITICAL GAP – Booking Persistence**: Confirming a booking generates a local UI reference (`SHP-NE-8063`) but does **not** insert a record into a `bookings` or `shipments` database table on Supabase.
- [!] **CRITICAL GAP – Payment Processing**: No digital payment gateway or backend payment status record.

---

### 🔴 3. Real-Time Tracking & Customer Notifications — **20% Production Ready**
- [x] **Tracking UI & Timeline** (`ActiveBookingsScreen.kt`, `TrackDetailScreen.kt`): Polished Compose UI displaying shipment status steps.
- [x] **Proof of Delivery (POD) View** (`PODScreen.kt`): Displays POD image & receiver details.
- [!] **CRITICAL GAP – Live Truck Telemetry**: Live map location on customer tracking screen currently uses mock coordinates. **No Supabase Realtime channel or Socket.IO pipeline** is streaming live GPS telemetry from the driver's phone.
- [!] **CRITICAL GAP – Push Notifications**: Firebase Cloud Messaging (FCM) is not installed. Status transitions do not send push alerts to customer devices.

---

### 🟡 4. Driver Deliveries & Status Updates — **40% Production Ready**
- [x] **Driver Home Dashboard** (`DriverHomeScreen.kt`): UI displaying active jobs, trip details, and status action buttons.
- [x] **Status Update Triggers**: Driver can tap buttons to update trip state (In Transit, Arrived, Delivered).
- [!] **CRITICAL GAP – Real Job Dispatch**: Trip list currently renders hardcoded sample data rather than querying assigned driver jobs from a `trips` table.
- [!] **CRITICAL GAP – Driver Foreground GPS Service**: Driver location sharing relies on active screen display. When the driver locks their screen or switches to a navigation app (e.g. Waze / Google Maps), tracking stops. A **Foreground Location Service with a ongoing notification** is strictly required.

---

### 🔴 5. Offline Resilience & Messaging — **0% Production Ready**
- [!] **CRITICAL GAP – Offline Store**: In rural Luzon regions with weak cell service, status updates and GPS updates fail if offline. Needs a local Room database or queue for auto-sync upon reconnection.
- [!] **CRITICAL GAP – Dispatcher Chat**: No real-time messaging pipeline between driver and dispatcher.

---

## 🛠️ Production Launch Readiness Checklist (Action Plan)

To transition Tanaw from **UI Prototype** to **Production-Ready Release**:

### Phase 1: Core Database Persistence (High Priority)
1. **Database Schema Integration**: Create `bookings` and `shipments` table schema in Supabase.
2. **Booking Submission**: Connect `BookingSummaryScreen` confirm button to insert real row into Supabase `bookings`.
3. **Driver Trip Query**: Update `DriverHomeScreen` to fetch real assigned shipments where `driver_id = auth.uid()`.

### Phase 2: Live Tracking & Telemetry (High Priority)
4. **Driver Foreground Service**: Create an Android `ForegroundService` with `NOTIFICATION_SERVICE` to capture high-accuracy GPS coordinates continuously during trips.
5. **Realtime GPS Streaming**: Broadcast driver GPS coordinates to Supabase Realtime / Postgrest `driver_locations` table.
6. **Customer Map Stream**: Subscribe `TrackDetailScreen` to Supabase Realtime channel for live driver location updates.

### Phase 3: Notifications & Infrastructure (Medium Priority)
7. **Firebase Cloud Messaging (FCM)**: Integrate FCM for assignment and delivery status push notifications.
8. **Supabase Storage for POD**: Upload captured camera photos from `PODScreen` to Supabase Storage bucket.
9. **Offline Sync Queue**: Implement Room DB store for queueing location updates during signal loss on Luzon highways.
