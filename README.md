# IlyPhoto — Cross-Platform Photography Studio System

[![Platform: Android](https://img.shields.io/badge/Platform-Android%20%7C%20Kotlin-brightgreen.svg)](https://developer.android.com)
[![Platform: Web](https://img.shields.io/badge/Platform-ASP.NET%20Core%20%7C%20Web-blue.svg)](https://dotnet.microsoft.com)
[![Cloud Backend: Azure](https://img.shields.io/badge/Backend-Microsoft%20Azure%20App%20Service-0078D4.svg)](https://azure.microsoft.com)
[![Database: Azure Cloud](https://img.shields.io/badge/Database-Azure%20Table%20Storage%20%2F%20Cosmos%20DB-orange.svg)](https://azure.microsoft.com)

**IlyPhoto** is a full-featured, cross-platform photography studio and client management system. It enables photographers to showcase their portfolio, manage bookings and invoices, and handle customer enquiries and reviews, while providing clients with a seamless booking and review experience across mobile (Android) and web.

All platforms connect to a unified **Microsoft Azure Cloud Backend**, ensuring real-time data synchronization across every device.

---

## 🌟 Key Features

- **Cross-Platform Synchronization**: Instant synchronization across Android mobile and Web applications via Azure Cloud.
- **Unified Authentication**: Secure authentication for both Clients and Photographers/Admins.
- **Interactive Portfolio & Gallery**: Browse high-resolution photography categorized by Wedding, Portrait, Events, and Commercial.
- **Booking & Scheduling**: Reserve photography sessions, choose package types, and track booking statuses in real time.
- **Automated Invoicing & Pricing**: Automatic calculation of totals, invoice generation, and status tracking (`Unpaid`, `Paid`).
- **Client Reviews & Ratings**: Submit star ratings and customer testimonials.
- **Direct Enquiries**: Client contact inquiries with tracking statuses.
- **Resilient Offline Fallback**: Cached sessions and graceful degradation when offline.

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
                                             │ HTTPS
                                             ▼
                                ┌───────────────────────────┐
                                │     Azure REST Backend    │
                                │    Azure App Service      │
                                └─────────────┬─────────────┘
                                              │
                      ┌───────────────────────┴───────────────────────┐
                      ▼                                               ▼
       ┌─────────────────────────────┐                 ┌─────────────────────────────┐
       │     Azure Cloud Database    │                 │     Azure Blob Storage      │
       │ Azure Table Storage/Cosmos  │                 │   High-Res Galleries &      │
       │  (users, bookings, photos,  │                 │   Portfolio Asset Store     │
       │    enquiries, reviews)      │                 │                             │
       └─────────────────────────────┘                 └─────────────────────────────┘
```

---

## 📁 Repository Structure

```text
IlyPhoto/
├── app/                            # Android Application (Kotlin, Jetpack, Coroutines)
│   ├── src/main/java/com/example/ilyphoto/
│   │   ├── data/                   # Azure repositories, models & API clients
│   │   │   ├── AzureApiClient.kt   # REST client communicating with Azure Cloud
│   │   │   ├── AzureConfig.kt      # Cloud endpoints and container configurations
│   │   │   ├── AzureProvider.kt    # Token and session state manager
│   │   │   ├── Models.kt           # Shared cross-platform domain entities
│   │   │   └── ServiceLocator.kt   # Dependency injection container
│   │   └── ...                     # Android UI fragments, navigation, view models
│   └── build.gradle.kts            # Android build dependencies
├── build.gradle.kts                # Project-level Gradle build configuration
├── .gitignore                      # Git ignore rules for Android Studio & build files
└── README.md                       # Complete documentation
```

---

## 🚀 Getting Started

### 1. Prerequisites

- **Java JDK**: Version 17 or higher
- **Android Studio**: Ladybug / Hedgehog or newer (with Android SDK 34+)
- **Android Device or Emulator**: Android 8.0 (API level 26) or higher

### 2. Building & Running the Android App

1. Clone or open the `IlyPhoto` directory in **Android Studio**:
   ```bash
   cd IlyPhoto
   ```
2. Allow Gradle to sync dependencies.
3. Build the project using Gradle:
   ```bash
   ./gradlew assembleDebug
   ```
4. Run the application on an Android device or emulator from Android Studio by selecting **Run > Run 'app'** (`Shift + F10`).

---

## ☁️ Azure Cloud Backend & Database

The backend services are hosted on **Microsoft Azure**:

- **Azure App Service**: `https://ilyphoto-api-st10212542.azurewebsites.net`
- **Azure Storage Account**: `ilyphotostorage`
  - **Tables**: `users`, `bookings`, `galleries`, `services`, `invoices`, `enquiries`, `reviews`
  - **Blob Containers**: `ilyphoto-galleries` (client galleries and portfolios)

### Core REST Endpoints:

| Endpoint | Method | Description |
|---|---|---|
| `/api/health` | GET | System health check and storage status |
| `/api/auth/register` | POST | Register a new client account |
| `/api/auth/login` | POST | Client and Photographer authentication |
| `/api/auth/me` | GET | Retrieve authenticated profile |
| `/api/auth/profile` | PUT | Update profile contact details & photo |
| `/api/bookings` | GET, POST | Retrieve and submit photoshoot bookings |
| `/api/bookings/{id}/status` | PATCH | Update booking status (`Confirmed`, `Pending`, etc.) |
| `/api/photos` | GET, POST | Retrieve portfolio gallery photos |
| `/api/enquiries` | GET, POST | Submit client inquiries |
| `/api/reviews` | GET, POST | Submit and view client reviews & ratings |
