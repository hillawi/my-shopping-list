# MyShoppingList

A shopping list app for Android, built with Kotlin and Jetpack Compose. Lists sync in real time across devices via Supabase.

## Features

- Real-time shared shopping list (Supabase Realtime + Postgrest)
- Email/password authentication (create account, sign in, sign out)
- Items organized by category (Produce, Dairy, Meat, Pantry, Bakery, Household, Frozen, Snacks, Beverages, General)
- Quantity with configurable units (pcs, kg, g, L, ml, pack, box, bottle)
- Recently purchased history
- Share list to clipboard
- Haptic feedback
- English and Arabic language support
- Splash screen and custom app icon

## Tech Stack

- Kotlin, Jetpack Compose, Material 3
- Supabase (Auth, Postgrest, Realtime) via the [supabase-kt](https://github.com/supabase-community/supabase-kt) SDK
- Ktor (OkHttp engine)

## Requirements

- Android Studio (latest stable)
- Android SDK 36 (compile/target), minimum SDK 34
- JDK 21

## Getting Started

1. Clone the repository.
2. Open the project in Android Studio and let Gradle sync.
3. Supabase connection details currently live in `SupabaseClient.kt`. If you're running against your own Supabase project, update the `supabaseUrl` and `supabaseKey` there.
4. Run the `app` configuration on an emulator or device (minSdk 34+).

## Project Structure

```
app/src/main/java/com/ahmedhillawi/myshoppinglist/
├── domain/       # Data models (ShoppingItem, ShoppingCategory, MeasurementUnit)
├── ui/           # Compose screens and components
├── viewmodel/    # ShoppingListViewModel
├── MainActivity.kt
└── SupabaseClient.kt
```

## Status

Version 1.0.0 — first release, currently running in production.
