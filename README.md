# AI Study Helper

An Android application that uses AI to help students study more effectively. Ask questions, summarize lecture notes, generate interactive quizzes, and review your history — all powered by the **Groq API** (Llama 3.1) with a fully offline local history via **Room**.

---

## Table of Contents

1. [Features](#features)
2. [Tech Stack](#tech-stack)
3. [Project Structure](#project-structure)
4. [Code Architecture](#code-architecture)
5. [Prerequisites](#prerequisites)
6. [Getting Started](#getting-started)
7. [Running Without Android Studio (CLI)](#running-without-android-studio-cli)
8. [Running With Android Studio](#running-with-android-studio)
9. [API Key Setup](#api-key-setup)
10. [Build Variants](#build-variants)
11. [Dependencies](#dependencies)

---

## Features

| Screen | What it does |
|--------|-------------|
| **Home** | Dashboard with 4 navigation cards |
| **Ask AI** | Type any question → get an AI answer → saved to history |
| **Summarize Notes** | Paste lecture notes → get a concise summary → saved to history |
| **Generate Quiz** | Enter a topic → get 5 interactive MCQ questions with instant feedback |
| **History** | Browse all past sessions, expand to read full responses, delete entries |

- `CircularProgressIndicator` while waiting for AI responses
- `Snackbar` on network errors or API failures with human-readable messages
- Empty-state message when history is empty
- All AI responses persisted locally in a Room SQLite database across app restarts

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin |
| UI | Jetpack Compose (Material 3) |
| Navigation | Navigation Compose |
| Architecture | MVVM — ViewModel + StateFlow |
| AI Backend | Groq API (`llama-3.1-8b-instant`) via OkHttp |
| JSON parsing | Gson |
| Local database | Room (SQLite) with KSP code generation |
| Async | Kotlin Coroutines (`viewModelScope`) |
| Build | Gradle 8.5, AGP 8.2.2, Kotlin 1.9.22 |
| Min SDK | 24 (Android 7.0) |
| Target SDK | 34 (Android 14) |

---

## Project Structure

```
project/
├── build.gradle.kts               # Root build file — plugin versions
├── settings.gradle.kts            # Module list + repository config
├── gradle.properties              # JVM args, AndroidX flags, JDK path
├── local.properties               # ⚠ GITIGNORED — sdk.dir + GROQ_API_KEY
├── gradlew / gradlew.bat          # Gradle wrapper scripts
├── gradle/
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties  # Gradle 8.5 distribution URL
└── app/
    ├── build.gradle.kts           # App-level dependencies, BuildConfig, Compose setup
    └── src/main/
        ├── AndroidManifest.xml    # INTERNET permission, Activity declaration
        ├── res/
        │   └── values/
        │       ├── strings.xml    # App name string resource
        │       └── themes.xml     # Material NoActionBar theme
        └── java/com/studyhelper/
            ├── StudyApp.kt        # Application class — lazy DB initialisation
            ├── MainActivity.kt    # Single Activity — NavHost + ViewModel wiring
            ├── data/
            │   ├── HistoryEntry.kt    # Room @Entity
            │   ├── HistoryDao.kt      # Room @Dao — insert / getAll / delete
            │   └── AppDatabase.kt     # RoomDatabase singleton
            ├── network/
            │   └── OpenAiService.kt   # Groq API calls via OkHttp (suspend fun)
            ├── viewmodel/
            │   └── StudyViewModel.kt  # Shared ViewModel + ViewModelFactory
            └── ui/
                ├── HomeScreen.kt      # Dashboard with 4 NavigationCard composables
                ├── AskAiScreen.kt     # Q&A screen
                ├── SummarizeScreen.kt # Notes summarisation screen
                ├── QuizScreen.kt      # Quiz generation + interactive answering
                └── HistoryScreen.kt   # Scrollable history list with expand/delete
```

---

## Code Architecture

### Overview

The app follows a clean **MVVM** pattern with a single Activity and Jetpack Compose for the entire UI.

```
UI (Composables)
     │  collectAsState()
     ▼
StudyViewModel          ←──── StateFlow / SharedFlow
     │  viewModelScope.launch
     ├──▶ OpenAiService.chat()   ──▶ Groq API (network)
     └──▶ HistoryDao             ──▶ Room SQLite (disk)
```

### `StudyApp.kt` — Application Class

```kotlin
class StudyApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
}
```

Initialises the Room database lazily so it is only created on first access. The `Application` class is registered in `AndroidManifest.xml` via `android:name=".StudyApp"`.

---

### `MainActivity.kt` — Single Activity

The entire app lives inside one `ComponentActivity`. On `onCreate`, it:

1. Creates a `NavController` for screen navigation
2. Instantiates `StudyViewModel` via `StudyViewModelFactory`, passing the Room DAO
3. Sets up a `SnackbarHostState` shared across all screens for error display
4. Declares a `NavHost` with 5 composable destinations: `home`, `ask`, `summarize`, `quiz`, `history`

Navigation is string-route based (e.g. `navController.navigate("ask")`).

---

### `data/` — Room Database Layer

#### `HistoryEntry.kt`
```kotlin
@Entity(tableName = "history_entries")
data class HistoryEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val type: String,      // "ASK" | "SUMMARY" | "QUIZ"
    val title: String,     // user question / topic / truncated notes
    val response: String,  // full AI text or raw JSON
    val createdAt: Long    // System.currentTimeMillis()
)
```

One table stores all three entry types, differentiated by the `type` field.

#### `HistoryDao.kt`
Three operations:
- `insertEntry(entry)` — `@Insert`, suspend
- `getAllEntries()` — `@Query` returning `Flow<List<HistoryEntry>>` ordered newest-first
- `deleteEntry(entry)` — `@Delete`, suspend

#### `AppDatabase.kt`
Thread-safe singleton using double-checked locking (`@Volatile` + `synchronized`). The database file is named `study_helper_db`.

---

### `network/OpenAiService.kt` — AI Network Layer

A Kotlin `object` (singleton) that makes HTTP POST requests to the Groq API using **OkHttp**.

- **Endpoint:** `https://api.groq.com/openai/v1/chat/completions`
- **Model:** `llama-3.1-8b-instant`
- **Authentication:** `Authorization: Bearer <GROQ_API_KEY>` header
- **Timeouts:** 60 s connect / read / write

The API key is injected at **build time** from `BuildConfig.GROQ_API_KEY` (never hardcoded).

The `chat(prompt, maxTokens)` suspend function:
1. Builds a JSON body with `{"model", "messages": [{"role":"user","content":prompt}], "max_tokens"}`
2. Executes the request on `Dispatchers.IO`
3. On success, extracts `choices[0].message.content` from the response
4. On failure, maps HTTP status codes to user-readable messages (401 → invalid key, 429 → rate limit, 5xx → server error)
5. Returns `Result<String>` — never throws

---

### `viewmodel/StudyViewModel.kt` — Shared ViewModel

One ViewModel shared across all AI screens. It exposes:

| State | Type | Description |
|-------|------|-------------|
| `askResponse` | `StateFlow<String>` | AI answer text |
| `askLoading` | `StateFlow<Boolean>` | Loading indicator for Ask screen |
| `summaryResponse` | `StateFlow<String>` | Summary text |
| `summaryLoading` | `StateFlow<Boolean>` | Loading indicator for Summary screen |
| `quizJson` | `StateFlow<String>` | Raw JSON string from AI |
| `quizLoading` | `StateFlow<Boolean>` | Loading indicator for Quiz screen |
| `error` | `SharedFlow<String>` | One-shot error messages → Snackbar |
| `historyEntries` | `StateFlow<List<HistoryEntry>>` | Live Room DB stream |

**`askAi(question)`** — calls `OpenAiService.chat(question)`, saves result as `HistoryEntry(type="ASK")`.

**`summarize(notes)`** — prepends a summarisation prompt, calls `OpenAiService.chat(...)`, saves result as `HistoryEntry(type="SUMMARY")`. The title stored is the first 80 characters of the notes.

**`generateQuiz(topic)`** — sends a structured prompt instructing the model to return **only** a JSON array of 5 MCQ objects:
```json
[{"question":"...","options":["A","B","C","D"],"answer":0}]
```
Saves raw JSON as `HistoryEntry(type="QUIZ")`.

`StudyViewModelFactory` — a simple `ViewModelProvider.Factory` that passes the `HistoryDao` to the ViewModel constructor (no Hilt/Dagger needed).

---

### `ui/` — Compose Screens

#### `HomeScreen.kt`
Four `NavigationCard` composables in a `Column`. Each card is a `Card` with a `Row` containing a Material icon and two `Text` labels (title + description). Clicking navigates to the matching route.

#### `AskAiScreen.kt`
- `OutlinedTextField` for the question (max 4 lines)
- `Button` disabled while loading or input is blank; shows `CircularProgressIndicator` + "Thinking…" when loading
- `LaunchedEffect(Unit)` collects `viewModel.error` via `collectLatest` → `snackbarHostState.showSnackbar()`
- Result displayed in a scrollable `Card` with `weight(1f)` to fill remaining height

#### `SummarizeScreen.kt`
Same structure as `AskAiScreen` but with a taller `OutlinedTextField` (fixed `height(200.dp)`, max 20 lines) for pasting lecture notes.

#### `QuizScreen.kt`
The most complex screen:

1. After `quizJson` is populated, it is parsed with Gson into `List<QuizQuestion>`:
   ```kotlin
   data class QuizQuestion(val question: String, val options: List<String>, val answer: Int)
   ```
   The parser extracts the JSON array even if the model wraps it in extra text.
2. `selectedAnswers: SnapshotStateMap<Int, Int>` tracks which option the user picked per question.
3. `submitted: Boolean` gates the Submit button and enables colour feedback.
4. Each `QuizQuestionCard` renders `RadioButton`s for each option. After submission:
   - Correct selected → green (`0xFF4CAF50`)
   - Wrong selected → red (`0xFFF44336`)
   - Correct not selected → faded green (shows the right answer)
5. Score displayed as "Score: X / 5" after submission.

#### `HistoryScreen.kt`
- Empty state: centred `Text` when `entries` is empty
- `LazyColumn` with `animateContentSize()` on each card for smooth expand/collapse
- Each `HistoryItemCard` shows: `AssistChip` with type label, formatted date, truncated title
- Tapping expands to show full response text + a delete `IconButton`
- Dates formatted with `SimpleDateFormat("MMM dd, yyyy HH:mm")`

---

## Prerequisites

| Tool | Version | Required for |
|------|---------|-------------|
| JDK | 17 or 22 | Compiling Kotlin/Java |
| Android SDK | API 34 | Build tools |
| Android Build Tools | 34.x | APK packaging |
| Gradle | 8.5 (auto-downloaded) | Build system |
| `adb` (platform-tools) | any | Installing to device/emulator |
| Groq account | — | Free API key |

Get a free Groq API key at [console.groq.com](https://console.groq.com).

---

## Getting Started

### 1. Clone the repository

```bash
git clone https://github.com/omri-rayen/mobile_project.git
cd mobile_project
```

### 2. Set up `local.properties`

`local.properties` is gitignored for security. Create it manually at the project root:

```properties
# Path to your Android SDK installation
sdk.dir=/home/yourname/Android/Sdk        # Linux/macOS
# sdk.dir=C:\Users\yourname\AppData\Local\Android\Sdk   # Windows

# Your Groq API key — get one free at https://console.groq.com
GROQ_API_KEY=gsk_your_key_here
```

> **Security note:** Never commit `local.properties`. It is listed in `.gitignore`.

### 3. Verify your JDK

The project is configured for JDK 17 (source/target compatibility). If you have a different version:

- **JDK 22 installed:** The `gradle.properties` may contain `org.gradle.java.home` pointing to a specific path. Update it to match your JDK path, or delete that line and let Android Studio use its embedded JDK.
- **JDK 17:** No changes needed.

```bash
java -version  # Should be 17 or higher
```

---

## Running Without Android Studio (CLI)

### Build the APK

**Windows:**
```bat
set JAVA_HOME=C:\Program Files\Java\jdk-22
gradlew.bat assembleDebug
```

**Linux / macOS:**
```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
chmod +x gradlew
./gradlew assembleDebug
```

The APK will be at:
```
app/build/outputs/apk/debug/app-debug.apk
```

### Create and start an emulator (first time only)

```bash
# List available system images
sdkmanager --list | grep "system-images;android-33"

# Download the image (if not already downloaded)
sdkmanager "system-images;android-33;google_apis;x86_64"

# Create an AVD named "StudyHelper"
avdmanager create avd -n StudyHelper -k "system-images;android-33;google_apis;x86_64"

# Start the emulator (background)
emulator -avd StudyHelper -no-snapshot-load &
```

### Install and run

```bash
# Wait for the emulator to fully boot, then install
adb install app/build/outputs/apk/debug/app-debug.apk

# Or build + install in one step
./gradlew installDebug

# Launch the app
adb shell am start -n com.studyhelper/.MainActivity
```

### Useful ADB commands

```bash
adb devices                          # List connected devices/emulators
adb logcat -s AndroidRuntime:E       # Watch for crashes
adb shell am force-stop com.studyhelper   # Kill the app
adb uninstall com.studyhelper        # Uninstall
```

---

## Running With Android Studio

1. **Open:** File → Open → select the `mobile_project` folder (the one containing `settings.gradle.kts`)
2. **Gradle sync** will run automatically. Wait for "Gradle sync finished" in the status bar.
3. **JDK:** File → Settings → Build, Execution, Deployment → Build Tools → Gradle → set "Gradle JDK" to your installed JDK 17 or 22. Alternatively, delete the `org.gradle.java.home` line from `gradle.properties` to use Android Studio's embedded JDK.
4. **Run:** Select a device or emulator from the toolbar and click ▶ (Shift+F10).

> The `.idea/` folder does not exist in the repository — Android Studio creates it automatically on first open. This is normal.

---

## API Key Setup

The app reads the Groq API key at **build time** via `BuildConfig`:

```kotlin
// app/build.gradle.kts
buildConfigField(
    "String",
    "GROQ_API_KEY",
    "\"${localProperties.getProperty("GROQ_API_KEY", "")}\""
)
```

At runtime, `OpenAiService` injects it as an HTTP header:

```kotlin
.addHeader("Authorization", "Bearer ${BuildConfig.GROQ_API_KEY}")
```

**To rotate the API key:** edit `local.properties`, rebuild the app — no source code changes needed.

---

## Build Variants

| Task | Command | Output |
|------|---------|--------|
| Debug APK | `./gradlew assembleDebug` | `app/build/outputs/apk/debug/app-debug.apk` |
| Install debug | `./gradlew installDebug` | Installs on connected device |
| Release APK | `./gradlew assembleRelease` | Requires signing config |
| Clean | `./gradlew clean` | Deletes `build/` directories |

---

## Dependencies

| Library | Version | Purpose |
|---------|---------|---------|
| `androidx.core:core-ktx` | 1.12.0 | Kotlin extensions for Android core APIs |
| `androidx.lifecycle:lifecycle-runtime-ktx` | 2.7.0 | Lifecycle-aware coroutine scopes |
| `androidx.activity:activity-compose` | 1.8.2 | `setContent {}` for Compose in Activity |
| `androidx.compose:compose-bom` | 2024.02.00 | Compose Bill of Materials (version alignment) |
| `androidx.compose.ui:ui` | BOM | Core Compose UI runtime |
| `androidx.compose.material3:material3` | BOM | Material You components |
| `androidx.compose.material:material-icons-extended` | BOM | Extended Material icon set |
| `androidx.navigation:navigation-compose` | 2.7.7 | Compose navigation with `NavHost` |
| `androidx.lifecycle:lifecycle-viewmodel-compose` | 2.7.0 | `viewModel()` composable helper |
| `androidx.room:room-runtime` | 2.6.1 | Room SQLite abstraction layer |
| `androidx.room:room-ktx` | 2.6.1 | Coroutine and Flow extensions for Room |
| `androidx.room:room-compiler` | 2.6.1 | KSP annotation processor for Room |
| `com.squareup.okhttp3:okhttp` | 4.12.0 | HTTP client for Groq API calls |
| `com.google.code.gson:gson` | 2.10.1 | JSON serialisation / deserialisation |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | 1.7.3 | Coroutines for Android with `Dispatchers.Main` |

**Build plugins:**

| Plugin | Version |
|--------|---------|
| Android Gradle Plugin | 8.2.2 |
| Kotlin Android | 1.9.22 |
| KSP (Kotlin Symbol Processing) | 1.9.22-1.0.17 |
