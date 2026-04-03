# CloudfinApp

Cloudfin Android UI - Kotlin + Jetpack Compose

## Build

```bash
./gradlew assembleDebug
```

## Project Structure

```
CloudfinApp/
├── app/
│   ├── src/main/
│   │   ├── java/com/cloudfin/
│   │   │   ├── MainActivity.kt
│   │   │   ├── MainViewModel.kt
│   │   │   ├── model/
│   │   │   │   └── Models.kt
│   │   │   ├── data/
│   │   │   │   ├── api/
│   │   │   │   │   └── CloudfinApi.kt
│   │   │   │   └── repository/
│   │   │   │       └── CloudfinRepository.kt
│   │   │   └── ui/
│   │   │       ├── theme/
│   │   │       │   ├── Theme.kt
│   │   │       │   ├── Color.kt
│   │   │       │   └── WallpaperBackground.kt
│   │   │       ├── navigation/
│   │   │       │   └── BottomNavBar.kt
│   │   │       ├── screens/
│   │   │       │   ├── StatusScreen.kt
│   │   │       │   ├── ModulesScreen.kt
│   │   │       │   ├── NetworkScreen.kt
│   │   │       │   ├── SyncScreen.kt
│   │   │       │   └── SettingsScreen.kt
│   │   │       └── components/
│   │   │           └── ModuleCard.kt
│   │   └── res/
│   │       └── values/
│   │           └── colors.xml
│   └── build.gradle.kts
└── settings.gradle.kts
```

## Features

- **4 Theme Modes**: Dark, Light, System, Wallpaper
- **Wallpaper Background**: Custom image with adjustable overlay opacity
- **5-Tab Navigation**: Status, Modules, Network, Sync, Settings
- **Module Management**: Start/Stop/Configure P2P/TOR/I2P/CRDT/Storage modules
- **P2P Network View**: Connected peers list, disconnect, add new peers
- **CRDT Sync**: Document sync status, create/import/export documents
- **Settings**: Theme selection, wallpaper configuration, network settings
