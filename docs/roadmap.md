# Focus Launcher — Build Roadmap

Nov 3, 2026 · @Paliyo

## Goal: a launcher you can daily-drive in 10–15 evenings

v0.1 is done when you have used it as your only home screen for 7 days without switching back. Every phase ends with something you install on your phone and actually use, so problems surface early and you never have a half-built launcher you can't live with.

In scope for v0.1, matching the Figma screens:

- **Home:** clock, date, five text-only favorites.
- **App list:** alphabetical, text-only, type-to-launch search.
- **App options:** long-press sheet to favorite, hide, mark as distracting and set a daily limit.
- **Friction screen:** a pause before distracting apps, with today's usage.
- **Today panel:** to-dos, habits and quick capture, stored locally behind an interface so Doen can replace it later.

Out of scope for v0.1: icons, widgets, wallpaper, calendar, notification filtering, sync, and blocking apps opened from outside the launcher. These are in the backlog at the end.

The evening counts are rough guesses for someone comfortable with Kotlin but new to launcher APIs.

&#91;embedded content: roadmap · 5 phases, each with its gate\]

Don't start a phase until the previous gate holds on your own phone; the last gate is the v0.1 release.

## Stack and architecture

Kotlin and Jetpack Compose, one main activity, Room for anything you'd miss after a reinstall. Keep the dependency list short; you can add Hilt or Compose Navigation later if the app outgrows a simple setup.

| Decision | Choice | Why |
| --- | --- | --- |
| Language / UI | Kotlin, Jetpack Compose | Fast iteration on a custom, text-only UI |
| minSdk | 29 (Android 10) | Usage events with `ACTIVITY_RESUMED` / `ACTIVITY_PAUSED` start at API 29 |
| Activities | `HomeActivity` + separate `FrictionActivity` | A separate friction activity can later be launched by an accessibility service for apps opened from anywhere |
| State | ViewModel + `StateFlow` | Survives config changes, easy to test |
| DI | A small `AppContainer` built in `Application` | No framework needed at this size |
| Storage | Room for app settings, to-dos, habits, intent log; DataStore for preferences | Queryable, survives process death |
| Theme | Figma variables → a `LauncherColors` object; Geist + Geist Mono in `res/font` | One source of truth for the look |

Package layout:

```
me.paliyo.launcher
├── LauncherApp.kt            // Application, builds AppContainer
├── AppContainer.kt
├── HomeActivity.kt
├── data/
│   ├── apps/AppRepository.kt      // LauncherApps → Flow<List<AppEntry>>
│   ├── apps/AppSettingsDao.kt
│   ├── usage/UsageRepository.kt   // today's minutes and opens per app
│   ├── today/TodayRepository.kt   // interface: local now, Doen later
│   └── db/LauncherDatabase.kt
└── ui/
    ├── theme/      // Color.kt, Type.kt, Theme.kt
    ├── home/       // HomeScreen, HomeViewModel
    ├── apps/       // AppListScreen, AppListViewModel
    ├── options/    // AppOptionsSheet
    └── friction/   // FrictionActivity, FrictionScreen
```

Core data model:

```kotlin
data class AppEntry(
    val label: String,
    val component: ComponentName,
    val user: UserHandle,          // work profile apps share package names
)

@Entity
data class AppSettings(
    @PrimaryKey val key: String,   // flattened component + user serial
    val favoriteOrder: Int? = null,
    val hidden: Boolean = false,
    val distracting: Boolean = false,
    val dailyLimitMin: Int? = null,
)
```

## Phase 0–1: Setup and become a launcher (1–2 evenings)

By the end, pressing Home on your phone lands on a black screen with your clock.

**Phase 0 — setup**

- [ ] New project: Empty Compose Activity, Gradle Kotlin DSL, minSdk 29
- [ ] Git repo, first push
- [ ] Theme from Figma: the 9 color tokens, Geist and Geist Mono fonts, a type scale (clock 88 Light, title 30 Medium, app rows 20–24, body 16, mono meta 12)
- [ ] USB debugging on; leave the stock launcher installed as a fallback

**Phase 1 — become a launcher**

- [ ] Add the HOME intent filter to `HomeActivity` (snippet below)
- [ ] Black `windowBackground` in the theme, so there's no white flash when Home is pressed
- [ ] Edge-to-edge with light status bar icons
- [ ] `BackHandler` on home that does nothing (on the app list it closes the list)
- [ ] Override `onNewIntent`: a Home press while already home resets to the home view and clears search
- [ ] Lock to portrait for now

```xml
<activity
    android:name=".HomeActivity"
    android:launchMode="singleTask"
    android:excludeFromRecents="true"
    android:stateNotNeeded="true"
    android:screenOrientation="portrait"
    android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.HOME" />
        <category android:name="android.intent.category.DEFAULT" />
    </intent-filter>
</activity>
```

**Done when:** you've set it as the default home app and Home always lands on it.

## Phase 2: App list and search (2–3 evenings)

By the end, you can open any app by typing two or three letters and pressing Enter. This is the phase that makes the launcher usable as a daily driver.

- [ ] `AppRepository`: for each profile in `LauncherApps.getProfiles()`, call `getActivityList(null, user)` and map to `AppEntry`; sort labels with a locale-aware `Collator`
- [ ] Register a `LauncherApps.Callback` so installs, uninstalls and updates show up without a restart
- [ ] Launch with `launcherApps.startMainActivity(component, user, null, null)`, not a plain `Intent`, so work-profile apps open correctly
- [ ] App list screen: `LazyColumn` with sticky letter headers and the text rows from Figma
- [ ] Search field auto-focuses and opens the keyboard when the list opens
- [ ] Match on a normalized label (lowercase, accents stripped with `java.text.Normalizer`); rank prefix matches first, then word starts ("maps" for "google maps"), then contains
- [ ] The keyboard's Go action launches the top match
- [ ] Open the list from home with a swipe up and with the search button
- [ ] Close the list after launching an app and on a Home press

If some apps are missing from the list, add a `<queries>` block for the `MAIN` / `LAUNCHER` intent to the manifest; Android 11+ hides packages you haven't declared interest in.

**Done when:** you can find and open any app in under 2 seconds without scrolling.

## Phase 3: Home screen and options sheet (2–3 evenings)

By the end, your five daily apps are one tap from home, and every app's settings live behind a long-press.

- [ ] Room database with the `AppSettings` table; `AppRepository` joins installed apps with their settings
- [ ] Clock and date: a flow that emits once per minute, aligned to the minute boundary; respect `DateFormat.is24HourFormat()`
- [ ] Favorites: up to five, in a stored order, tap to launch
- [ ] Long-press any app (home or list) → `ModalBottomSheet` with:
  - [ ] Show on home
  - [ ] Hide from app list
  - [ ] Mark as distracting
  - [ ] Daily limit stepper (enabled only when distracting)
  - [ ] App info → `Settings.ACTION_APPLICATION_DETAILS_SETTINGS`
  - [ ] Uninstall → `Intent.ACTION_DELETE` with the package URI
- [ ] Hidden apps leave the list but still appear in search results, as in the Figma copy
- [ ] Favorites order: v0.1 keeps the order you added them; drag-to-reorder goes in the backlog

Skip the 7-day usage chart in the sheet for now; it needs the usage data from phase 4.

**Done when:** you go a full day without opening the app list for your everyday apps.

## Phase 4: Distraction layer (3–4 evenings)

By the end, every distracting app you open from the launcher goes through the pause screen, and you can see today's minutes next to it.

**Usage data**

- [ ] Check usage access with `AppOpsManager` (`OPSTR_GET_USAGE_STATS`); if it's missing, show a one-line prompt that opens `Settings.ACTION_USAGE_ACCESS_SETTINGS`
- [ ] `UsageRepository.today()`: `queryEvents` from local midnight to now, pair `ACTIVITY_RESUMED` with the next `ACTIVITY_PAUSED` per package, sum the durations and count the opens
- [ ] Compute it when the launcher becomes visible, not on a timer, so it costs no battery
- [ ] Show usage on distracting rows; switch to the accent color once over the limit

Avoid `queryUsageStats(INTERVAL_DAILY)` for "today": its buckets don't line up with your local midnight, so totals come out wrong in confusing ways.

**Friction screen**

- [ ] Launching a distracting app starts `FrictionActivity` instead, with the target component and user as extras
- [ ] Show the app name, minutes vs. limit, opens today and time since the last open
- [ ] Intent chips ("Reply to a message", "Post something", …) plus "Back to focus"
- [ ] "Open for 5 minutes" is disabled for a 10-second countdown, then launches the app
- [ ] Log every attempt to Room: package, time, chosen intent, opened or backed out
- [ ] Back to focus finishes the activity and returns home

The 5-minute session isn't enforced yet: the launcher can't see or close the app once it's open. Enforcing it needs the accessibility service in the backlog.

**Done when:** opening Instagram from your launcher always pauses first, and its minutes match what Digital Wellbeing shows within a few minutes.

## Phase 5: Today panel (2–3 evenings)

By the end, your home screen shows today's to-dos and habits, and you can capture a thought in two taps. Everything goes through one interface, so Doen can take over the data later without touching the UI.

```kotlin
interface TodayRepository {
    fun todos(): Flow<List<Todo>>          // open + completed today
    fun habits(): Flow<List<HabitWeek>>    // last 7 days + streak
    suspend fun toggleTodo(id: Long)
    suspend fun logHabit(id: Long, date: LocalDate)
    suspend fun capture(text: String)      // lands as an open to-do
}
```

- [ ] `LocalTodayRepository` backed by Room (`Todo`, `Habit`, `HabitLog` tables)
- [ ] Today list on home: tap to toggle, done items struck through, "2 / 5" counter
- [ ] Habit rows with the 7-day squares and streak from Figma; tap a habit to log today
- [ ] Quick capture: tap the bar → inline text field with keyboard; Enter saves and closes
- [ ] A plain manage screen to add, rename and delete to-dos and habits
- [ ] Next calendar event (optional): `CalendarContract.Instances` with `READ_CALENDAR`

Later, a `DoenTodayRepository` reads the same data from Doen through a `ContentProvider` or a shared Gradle module; swapping it in is one line in `AppContainer`.

**Done when:** you've checked off a week of to-dos from the home screen. That completes v0.1.

## Backlog after v0.1

Pick from this list based on what annoys you most during the v0.1 week. Enforcement is the biggest jump, because it's the first feature that reaches outside your own launcher.

| Feature | How | Size |
| --- | --- | --- |
| Block distracting apps opened from anywhere (notifications, recents, links) | `AccessibilityService` watching window changes; launch `FrictionActivity` over the app | L |
| Enforce "open for 5 minutes" | Same service plus a timer that sends you home when the session ends | M |
| Weekly stats screen | Intent log + usage events: minutes, opens, reasons given | M |
| 7-day usage chart in the options sheet | Reuse `UsageRepository` for the last 7 days | S |
| Focus sessions and schedules | Hide distracting apps during set hours; `AlarmManager` for start and end | M |
| System-wide grayscale toggle | Grant `WRITE_SECURE_SETTINGS` once over adb, then flip the display daltonizer to monochrome | S |
| Notification batching | `NotificationListenerService`; hold non-essential notifications until set times | L |
| Drag-to-reorder favorites | Compose drag-and-drop in a reorder mode | S |
| Doen integration | Swap in `DoenTodayRepository` | M |
| Settings backup | Export and import Room data as JSON | S |
| Widgets | `AppWidgetHost`; the most fiddly launcher API, and arguably off-brand | L |

## Gotchas and safety net

The main risk of daily-driving your own launcher is a crash loop on Home. Keep an escape route ready before Phase 1.

- **Escape route:** keep the stock launcher installed. To switch back, go to Settings → Apps → Default apps → Home app (the path varies by brand). If yours crashes on start, `adb uninstall me.paliyo.launcher` drops you straight back to the stock one.
- **Test the Home intent without a phone press:** `adb shell am start -a android.intent.action.MAIN -c android.intent.category.HOME`
- **Recents animations:** with gesture navigation, the recents screen and its animations belong to the system launcher. Third-party launchers can get plainer transitions on some phones; that isn't a bug in your code.
- **Process death:** Android can kill the launcher under memory pressure. Keep anything that matters in ViewModels and Room, not in-memory caches.
- **Work profile:** carry `UserHandle` everywhere and launch through `LauncherApps`, or work apps open as the wrong user or not at all.
- **Performance:** Compose debug builds are noticeably slower. Install a release build (signed with your debug key is fine) on the phone you daily-drive.
- **White flash:** if you see one on Home, the theme's `windowBackground` isn't black yet.
