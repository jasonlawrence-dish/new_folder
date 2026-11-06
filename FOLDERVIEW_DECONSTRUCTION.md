# Folderview 4.0.6 — Deconstruction & Root-Cause Analysis

**Package:** `com.dish.folderview` · **versionName:** 4.0.6 · **versionCode:** 14 **minSdk:** 14 (from manifest `minSdkVersion`) · built with **B4A (Basic4Android)**, MultiDex **APK:** `Folderview_4.0.6.apk` (deconstructed from `C:\Users\Jason\Documents\Samsung A11\newfolderview\`) **Analyzed:** 2026-11-04 · for Jason Lawrence (DISH Enterprise Mobility)

---

## 1. What's inside the APK

| Entry | Notes |
| --- | --- |
| `AndroidManifest.xml` | 10 activities (see below), permissions, Samsung icon-container meta |
| `classes.dex` | single dex (~464 KB), all B4A-generated logic |
| `assets/default.png` | the fallback/placeholder icon used when an app's real icon can't load |
| `assets/layout2.bal` | one B4A layout: `Activity` + `Panel1` (the folder panel) |
| `res/...` | standard B4A resources |

### Permissions requested

`MANAGE_EXTERNAL_STORAGE`, `READ/WRITE_EXTERNAL_STORAGE`, `QUERY_ALL_PACKAGES`, `GET_TASKS`, `KILL_BACKGROUND_PROCESSES`, `REBOOT`, `WRITE_SETTINGS`, `INTERNET`, `ACCESS_NETWORK_STATE`, `FOREGROUND_SERVICE`, `BATTERY_STATS`, `DUMP`.

---

## 2. The activity architecture — THIS IS THE BUG

Folderview is **not** data-driven for its folder set. Each folder is a **separate, hardcoded Android activity**, and the folder names + labels are compiled into the manifest:

| Activity (class) | Folder label (manifest) |
| --- | --- |
| `.main` | **Wireless** |
| `.techtools` | Tech Tools |
| `.utilities` | Utilities |
| `.smarthome` | Smart Home |
| `.dishnet` | Satellite Broadband |
| `.support` | Support |
| `.gsuite` | Google Apps |
| `.crowncastle` | Crown Castle |
| `.createhomescreen` | Import Settings (draws the whole home screen / grid) |
| `.starter` | boot/launch service (`.starter$starter_BR` receiver) |

Hardcoded section tokens confirmed in `classes.dex`: `Folder:Wireless`, `Folder:TechTools`, `Folder:Utilities`, `Folder:SmartHome`, `Folder:dishNET`, `Folder:Support`, `Folder:GSuite`.

> **Consequence #1 — custom folder names crash.** The parser looks up its *own* known section names in `wirelessfolder.txt`. A name it doesn't know (e.g. `Folder:BoostDiag`) → lookup returns null → `String.equals()` on null → **FATAL NPE at **`main.java:584`. (This is exactly the crash we chased on 2026-11-03.)

> **Consequence #2 — empty sections crash.** If a recognized section has `NumberOfApps:0`, opening that folder's activity NPEs in that activity (observed `smarthome.java:578`). A section must have ≥1 *installed* app.

> **Consequence #3 — the junk grid.** `.createhomescreen` draws the **full built-in folder grid** (Wireless, Tech Tools, Utilities, Smart Home, Satellite Broadband, Support, Google Apps, Crown Castle, Import Settings) with static placeholder icons, regardless of what's in `wirelessfolder.txt`. On the WS1-Launcher kiosk this grid is what shows when you tap the Folderview icon — folders pointing at apps that aren't installed, and the "Wireless" folder appears to nest inside itself (the grid contains a Wireless tile that re-opens `.main`).

---

## 3. The orientation lock (the portrait bug)

DEX references confirm Folderview drives orientation itself via B4A `Phone`:

- `SetScreenOrientation`, `setRequestedOrientation`
- `PhoneOrientation`, `isPortrait`, `_orientationchanged`

The folder activities **force portrait** to lay out their panel, so when the tablet is in landscape the folder content pops into portrait. There is no responsive/landscape layout — `assets/layout2.bal` is a single fixed `Panel1` with absolute `left/top/width/height` anchors.

---

## 4. How the settings file is actually read

- File name: `wirelessfolder.txt` (all lowercase), read from **external storage root** (DEX: `getDirRootExternal`, `getExternalStorageDirectory`, `isExternalStorageManager`).
- Parse flow (DEX tokens): read section header `Folder:<Name>` → read `NumberOfApps:` / `StringNumApps=` → `ReadList` / `Split` / `Split2` each subsequent line on commas → logs `NumApps= `, `App Name is `, `Actual Apps is `, `SkipApp`.
- Line format per entry (comma-separated, **no spaces**):- `app,<Label>,<processName>,<icon|default>` — `default` = use app's real launcher icon
- `web,<Label>,<FULL_URL>,/.pics/<icon>.png` — **icon path REQUIRED (never **`default`**)**
- `zinc,<Label>,<hotlineURL>,/.pics/<icon>.png`
- Icon handling: `GetApplicationIcon` for apps; for web/zinc it loads the `.pics` file; `assets/default.png` is the fallback. Also bundles Material Icons font (`b4x_materialicons.ttf`).
- Not-installed apps are skipped (`SkipApp`), so one file can serve multiple roles.

---

## 5. Why it's fundamentally wrong for the A11 (SM-X200 / AE / WS1 Launcher)

- The A11 is **AE fully-managed**, WS1 Intelligent Hub is the **sole Device Owner**.
- Folderview expects to draw/lock the **native** home screen via Knox (the Samsung staging path). On an AE single-controller device those Knox calls don't take, so Folderview can only run as a **standalone app showing its internal grid** — which is the buggy behavior in the screenshots.
- WS1 Launcher already does native folders correctly on this device type. Folderview is the wrong tool here and fights the platform.

---

## 6. Requirements for `newfolderview` (the replica + improvements)

### Must FIX (current bugs)

1. **Data-driven folders — no hardcoded names.** Read folder set entirely from the settings file. Any `Folder:<Name>` works; never crash on an unknown name.
2. **Tolerate empty sections.** `NumberOfApps:0` (or a missing section) renders an empty folder or is skipped — never crash.
3. **Null-safe parser.** Never call `.equals()` on a possibly-null token. Trim CR/LF/BOM. Ignore blank lines. If `NumberOfApps` count mismatches actual lines, use the actual line count (don't trust the header blindly) and log a warning instead of crashing.
4. **Respect orientation.** Support both portrait AND landscape (responsive grid). Do NOT force `setRequestedOrientation`. Let `configChanges` handle rotation.
5. **No forced built-in grid / no self-nesting.** A folder must not contain a tile that reopens itself. The home grid should reflect ONLY the sections defined in the file.

### Should ADD (features current version lacks)

- Configurable file name/path (not hardcoded to one name) and optional multiple files.
- Responsive grid: column count derived from screen width/orientation.
- Graceful icon fallback (keep `default.png` behavior) + optional per-item icon override.
- Better logging surfaced to a log file on-device for field troubleshooting.
- Optional single-folder/kiosk mode (for Boost-style tablets: one folder, no grid).
- Validate entries at load; show a small on-screen notice for bad lines instead of crashing.

### KEEP (compatibility)

- Same `wirelessfolder.txt` line grammar (`app,`/`web,`/`zinc,` + label + process/URL + icon).
- `default` icon keyword for `app` entries; required icon path for `web`/`zinc`.
- Skip-if-not-installed behavior.
- Read from external storage root by default (so existing WS1 file-actions still deliver it).

### Target platform notes

- Package name for the replica: TBD (must differ from `com.dish.folderview` if both installed; same package if it's a drop-in replacement — then it needs the same signing key).
- minSdk/targetSdk: raise target for Android 14 (SM-X200); keep compatible with S22/S24/S25.
- Build: B4A (match Rick-app tooling) OR Kotlin/Android Studio (like DISH Tools / DISHStaging V2). Decision pending — see Section 7.

---

## 7. Open decisions before coding

1. **Build stack:** B4A (fastest parity, matches source lineage) vs. Kotlin (modern, matches DISHStaging V2 / DISH Tools direction).
2. **Drop-in vs. new package:** reuse `com.dish.folderview` (needs original keystore from the enterprise password manager) vs. new package `com.dish.newfolderview`.
3. **Does the A11 even need it?** On WS1 Launcher, native folders already work. Confirm the replica is for the broader native-launcher (Samsung staging) fleet, not just this tablet.

