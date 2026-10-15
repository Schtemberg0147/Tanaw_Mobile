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
| **Feature Count** | **23** | **11** | **5** | **39** |
| **Percentage** | **59.0%** | **28.2%** | **12.8%** | **100%** |

> 🚨 **Senior Architect Verdict**:  
> The mobile app has an **excellent UI foundation and solid Authentication & Driver pipelines (~59% production-ready)**. With the driver-side "My Trips" and "Profile" screens now fully connected to Supabase (Postgrest + Realtime), driver stats, period filtering, on-time rate calculations, and dynamic compliance tracking are production-ready. Crucial customer booking DB persistence and driver foreground GPS tracking services remain the final major milestones before production release.

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

### 🟢 4. Driver Deliveries, "My Trips", & Driver Profile — **85% Production Ready**
- [x] **Driver Home Dashboard** (`DriverHomeScreen.kt`, `DriverHomeViewModel.kt`): Fully data-driven with active trip assignments, realtime updates, and status action triggers.
- [x] **Driver "My Trips" Screen** (`MyTripsScreen.kt`, `MyTripsViewModel.kt`): **FULLY FUNCTIONAL**.
  - Server-side stats card via Postgres RPC `get_driver_monthly_stats` (Monthly Completed Trips, Total Allowance, On-Time Rate % or `"—"`).
  - Period Filter Bottom Sheet (Asia/Manila monthly selector & "All Time").
  - Tab Filtering (`All`, `Completed`, `Ongoing`, `Cancelled`) mapped to DB statuses with status-driven card styling (`TripStatus`, `TripCardStyle`).
  - Pagination (20 items/page) with pull-to-refresh and embedded PostgREST joins.
  - Live Supabase Realtime channel subscription updating list and stats on status changes.
- [x] **Driver "Profile" & Compliance Engine** (`DriverProfileScreen.kt`, `DriverProfileViewModel.kt`): **FULLY FUNCTIONAL**.
  - Header with dynamic avatar initials (`RC`), full name, assigned truck make/model & plate (or "No vehicle assigned"), and "Driver since Month Year".
  - Dynamic Compliance Status section querying `public.compliance_documents`.
  - On-device pure status badge calculation (`ComplianceStatus.from(expiryDate, today)` with 60-day threshold constant): `EXPIRED` (Red), `EXPIRING SOON` (Orange), `VALID` (Green).
  - Dynamic document sorting (urgent items first) and Info Banner.
  - Personal Information section & Supabase Auth sign-out clearing backstack.
- [!] **CRITICAL GAP – Driver Foreground GPS Service**: Driver location sharing relies on active screen display. When the driver locks their screen or switches to a navigation app (e.g. Waze / Google Maps), tracking stops. A **Foreground Location Service with an ongoing notification** is strictly required.

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

### Phase 2: Live Tracking & Telemetry (High Priority)
3. **Driver Foreground Service**: Create an Android `ForegroundService` with `NOTIFICATION_SERVICE` to capture high-accuracy GPS coordinates continuously during trips.
4. **Realtime GPS Streaming**: Broadcast driver GPS coordinates to Supabase Realtime / Postgrest `driver_locations` table.
5. **Customer Map Stream**: Subscribe `TrackDetailScreen` to Supabase Realtime channel for live driver location updates.

### Phase 3: Notifications & Infrastructure (Medium Priority)
6. **Firebase Cloud Messaging (FCM)**: Integrate FCM for assignment and delivery status push notifications.
7. **Supabase Storage for POD**: Upload captured camera photos from `PODScreen` to Supabase Storage bucket.
8. **Offline Sync Queue**: Implement Room DB store for queueing location updates during signal loss on Luzon highways.
