# IlyPhoto — Cross-Platform Photography Studio & Community Outreach System

[![Platform: Android](https://img.shields.com/badge/Platform-Android%20%7C%20Kotlin-brightgreen.svg)](https://developer.android.com)
[![Platform: Web](https://img.shields.com/badge/Platform-ASP.NET%20Core%20%7C%20Web-blue.svg)](https://dotnet.microsoft.com)
[![Cloud Backend: Azure](https://img.shields.com/badge/Backend-Microsoft%20Azure%20App%20Service-0078D4.svg)](https://azure.microsoft.com)
[![Database: Azure Table Storage & Blob Storage](https://img.shields.com/badge/Database-Azure%20Table%20Storage%20%26%20Blob%20Storage-orange.svg)](https://azure.microsoft.com)

**IlyPhoto** is a cross-platform photography booking, client gallery, invoicing, and community outreach system built for **ILY Photo Studio** (owned by **Aysha Allie**, 8 Green Way, Strandfontein, Cape Town). All platforms (Android Native App and ASP.NET Core Web Portal) connect to a unified **Microsoft Azure App Service REST API**, **Azure Table Storage**, and **Azure Blob Storage** backend.

---

## 🤝 Strandfontein Community & NGO Social Impact

Rooted in Strandfontein and the Cape Flats, **ILY Photo** addresses a direct community and NGO need alongside its commercial studio packages:
- **Subsidised Youth & Matric Farewell Photography**: Under-resourced Matric learners referred by local high schools and youth development NGOs can book the **Community & NGO Outreach** package (`R200 for 2 Hours`) so financial barriers do not prevent learners from celebrating milestone achievements.
- **NGO & Charity Event Documentation**: Registered non-profit organisations, soup kitchens, and faith-based community programmes across Strandfontein and Mitchells Plain use the platform to book event coverage and receive cloud-hosted Azure Blob galleries for donor reporting and community storytelling.

---

## 🏗️ System Architecture

```text
                      ┌──────────────────────────────────────────────┐
                      │              Clients & Devices               │
                      └───────┬──────────────────────────────┬───────┘
                              │                              │
                     REST API │                     REST API │
                     (JSON)   │                     (JSON)   │
                              ▼                              ▼
                 ┌─────────────────────────┐    ┌─────────────────────────┐
                 │   Android Native App    │    │    Web Client Portal    │
                 │    (Kotlin / MVVM)      │    │  (ASP.NET Core / HTML5) │
                 └────────────┬────────────┘    └────────────┬────────────┘
                              │                              │
                              └──────────────┬───────────────┘
                                             │ HTTPS (Bearer Token)
                                             ▼
                                ┌───────────────────────────┐
                                │     Azure REST Backend    │
                                │    Azure App Service      │
                                └─────────────┬─────────────┘
                                              │
                      ┌───────────────────────┴───────────────────────┐
                      ▼                                               ▼
       ┌─────────────────────────────┐                 ┌─────────────────────────────┐
       │     Azure Table Storage     │                 │     Azure Blob Storage      │
       │  (`Users`, `Bookings`,      │                 │   Container:                │
       │   `Invoices`, `Galleries`,  │                 │   `ilyphoto-galleries`      │
       │   `Photos`, `Services`,     │                 │   (High-Res Client Albums   │
       │   `Enquiries`, `Reviews`,   │                 │    & SAS-Protected Assets)  │
       │   `BlockedDates`)           │                 │                             │
       └─────────────────────────────┘                 └─────────────────────────────┘
```

---

## 🗄️ Cloud Database Schema, Table Keys, Client Links & Integrity Rules

All structured records are stored in **Azure Table Storage** (`ilyphotostorage`), while binary high-resolution photographs are stored in **Azure Blob Storage** (`ilyphoto-galleries`).

### 1. Azure Table Storage Keys (`PartitionKey` & `RowKey`)

| Table Name | `PartitionKey` | `RowKey` (Primary Key) | Foreign Key / Client Link | Description |
|---|---|---|---|---|
| `Users` | `"USER"` (`CLIENT` / `ADMIN` role) | `UserId` / `ClientId` (e.g. `CL-1001`) | Primary Client Identity (`ClientId`, `Email`) | Authenticated client & photographer profiles |
| `Bookings` | `"BOOKING"` | `BookingId` (e.g. `BK-2001`) | `ClientId` (`CL-xxxx`) + `ClientEmail` | Photoshoot date, time, venue, package, duration, price, and status |
| `Invoices` | `"INVOICE"` | `InvoiceNumber` (e.g. `INV-2001`) | `BookingId` (`BK-xxxx`) + `ClientId` (`CL-xxxx`) | Line items, total amount due, EFT reference, and payment status |
| `Galleries` | `"GALLERY"` | `GalleryId` (e.g. `GAL-3001`) | `ClientId` (`CL-xxxx`) + `BookingId` (`BK-xxxx`) | Private client photo albums linked to Azure Blob URLs |
| `Photos` | `"PHOTO"` | `PhotoId` (UUID) | `ClientId` + `StoragePath` (Blob URI) | Individual gallery/portfolio photo metadata and Blob SAS URLs |
| `Services` | `"SERVICE"` | `ServiceId` (`SVC-01`..`SVC-05`) |Referenced by `Bookings.ServiceName` | Studio & NGO Outreach photography packages |
| `Enquiries` | `"ENQUIRY"` | `EnquiryId` (`ENQ-xxxx`) | `ClientId` + `Email` | Client contact messages and photographer responses |
| `Reviews` | `"REVIEW"` | `ReviewId` (`ClientId`) | `ClientId` (1 review per client) | Verified client testimonials |
| `BlockedDates` | `"BLOCKED"` | `NormalizedDateKey` (`yyyy-MM-dd`) | Created by Photographer | Unavailable studio dates |

### 2. Referential Links & Data Integrity Rules

1. **Client-Scoped Isolation (`ClientId` Link)**:
   - Every `Booking`, `Invoice`, and `Gallery`/`Photo` record stores the owning client's `ClientId` (`CL-xxxx`), `Uid`, and `ClientEmail`.
   - On Android, `UserProfile.matchesBooking()` and `UserProfile.matchesPhoto()` enforce strict client scoping so clients can only view, reschedule, or download their own records.
2. **Authoritative Cloud Availability & Conflict Prevention**:
   - Before displaying availability or submitting a new/rescheduled booking, `BookingRepository.checkAvailability()`, `createBooking()`, and `updateBooking()` always call `fetchLatestBookings()` from Azure Table Storage first (never relying on stale local cache).
   - `Booking.normalizeDateKey()` normalizes dates across Android (`"04 September 2026"`) and Web (`"2026-09-04"`) into ISO `yyyy-MM-dd` format so cross-platform bookings on the same date are accurately detected.
   - Cancelled and Rejected bookings release their date reservation; Paid bookings cannot be cancelled via the client app.
3. **Full Cross-Device Rescheduling Persistence**:
   - Amending or rescheduling a booking on mobile invokes `AzureApiClient.updateBooking(booking)`, persisting all amended fields (`serviceName`, `servicePrice`, `durationHours`, `eventDate`, `eventTime`, `eventLocation`, `requirements`, `clientPhone`, and `status = "Rescheduled"`) to Azure Table Storage before updating local state.
4. **Authentication & Session Protection**:
   - `AuthRepository.signIn()` validates credentials strictly against `POST /api/auth/login` over HTTPS and rejects failed authentication without falling back to cached profiles.
   - `AzureProvider` validates token structure, rejects placeholder offline tokens, enforces a 24-hour session TTL, and verifies a SHA-256 session integrity signature (`KEY_SESSION_SIGNATURE`) before restoring a cached session.
5. **Accurate Error Propagation**:
   - Registration (`AuthRepository.register`), password recovery (`AuthRepository.sendPasswordReset`), profile saves (`AuthRepository.updateProfileDetails`), and booking writes (`BookingRepository`) verify cloud persistence before reporting success and surface descriptive user-facing errors on failure.

---

## 🧪 Running Unit Tests & Building the App

Run the automated JUnit test suite (covering booking conflict detection, cross-platform date normalization, full rescheduling field persistence, cloud-first availability checks, authentication rejection on invalid password even when a cached profile exists, and error propagation for failed registration/reset/save requests):

```bash
./gradlew testDebugUnitTest
```

Build the debug APK:

```bash
./gradlew assembleDebug
```
