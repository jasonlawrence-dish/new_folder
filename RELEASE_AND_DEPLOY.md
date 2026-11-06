# New Folder — Signed Release & WS1 Deploy Guide

App: **New Folder** (`com.dish.newfolderview`) · current version **1.0.3 / code 4** Target: Samsung SM-X200 (A11), Android 14, AE fully-managed, WS1 Launcher kiosk.

---

## A. Bug review result (2026-11-04)

Reviewed FolderActivity / FolderAdapter / ItemLauncher / ConfigParser / ConfigSource / IconLoader.

- **FIXED:** folder no longer closes when a tile fails to launch (e.g. app not installed / blocked). `ItemLauncher.launch()` now returns Boolean; the folder only `finish()`es on success. Previously it always closed, hiding the failure.
- No crash paths remain: parser is null-safe/count-free, launcher catches all throwables, icon loader falls back to the folder glyph, config loader tries managed-config → /sdcard → filesDir and degrades to an empty-state notice.
- Orientation reflow verified on-device (landscape/portrait).
- Floating popover + dim verified on-device (matches native folder look).

### Known non-issues (by design)

- `Resources$NotFoundException 0x7f0d0001` in logcat = benign Samsung resource offload warning on a background thread, not fatal.
- WS1 Launcher allowlist still governs what can actually launch — folder contents must also be allowlisted. This is a WS1 profile concern, not an app bug.

---

## B. Enhancements / suggestions (not blocking release)

Pick any to add later:

1. **Open animation** — scale/fade-in so the folder "pops" like a native folder.
2. **Multi-folder tiles** — deep link `newfolderview://folder/<Name>` already works; add WS1 web-link tiles per folder if the launcher supports URL tiles.
3. **Drop MANAGE_EXTERNAL_STORAGE** once deploying via managed config only — removes the scary all-files permission entirely (AE-clean).
4. **Icon from managed config** — allow base64 icons in the config so web/zinc icons don't need a /.pics file pushed separately.
5. **Columns tuned to item count + screen** — currently capped at 4; could widen on large tablets in landscape.
6. **Long-press** — optional app-info or nothing (kiosk usually wants nothing).

---

## C. Generate the release keystore (ONCE)

Keystore + passwords live in the **enterprise password manager**. Never commit them.

From the project root (or Android Studio Terminal), with the JDK keytool on PATH:

```
keytool -genkeypair -v ^
  -keystore newfolderview-release.jks ^
  -alias newfolderview ^
  -keyalg RSA -keysize 2048 -validity 10000 ^
  -storetype JKS

```

Answer the prompts (CN=DISH Enterprise Mobility, etc.). Then:

1. Move `newfolderview-release.jks` to the project root (git-ignored).
2. Copy `keystore.properties.template` → `keystore.properties` and fill in:``` storeFile=../newfolderview-release.jks storePassword=******** keyAlias=newfolderview keyPassword=********

```
3. Store the .jks + both passwords in the password manager immediately.

> Signing scheme note (from prior Rick-app experience): minSdk here is 24, so v2/v3 signing is what matters; apksigner's default `verify` may report "v1 scheme: false" even when fine. WS1/PackageManager accept v2/v3. If WS1 ever rejects for v1, force a check with `apksigner verify -v --min-sdk-version 16 --max-sdk-version 35 <apk>`.

---

## D. Build the signed release APK

Android Studio:

- **Build → Generate Signed Bundle / APK → APK**, choose the keystore, pick **release**. OR, since keystore.properties is wired into build.gradle.kts, just select the **release** build variant and Build → Build APK(s).

Output: `app/build/outputs/apk/release/app-release.apk`

**Bump the version every build** (standing rule): edit `version.properties` or run the `bumpVersion` Gradle task before each release build.

---

## E. Deploy & test via WS1

1. **Upload** `app-release.apk` to WS1 as an **Internal Application**; assign to the **boostdiag** OG (test group / single device first).
2. **Allowlist it in the Launcher profile** — add `com.dish.newfolderview` to the "Boost Diag - Launcher - Multi Apps" AllowedApplicationId (and give it a tile). Set visibility = True.
3. **Deliver the folder config** — preferred: **WS1 Managed App Configuration**. In the app's Application Configuration, set:- `config` = (paste the full folders.txt text)
- `defaultFolder` = Boost (optional; else first folder shows) This needs NO file and NO storage permission. Fallback: push `folders.txt` to `/sdcard/` via a file-action + grant All-Files-Access.
4. **Icons for web/zinc tiles** — if using a `/.pics/*.png` icon path, ensure the png is on the device (`/sdcard/.pics/`). (Managed-config base64 icons = future enhancement.)
5. **Test on ONE device**: open the folder from the Launcher tile, confirm all tiles render in BOTH orientations, each app launches, tap-outside dismisses, and a not-installed item keeps the folder open with a toast.
6. **Roll out gradually** once the single-device test passes. **Disable (don't delete)** the old Folderview product/tile for rollback.

### Boost frost note

Boost Unlock deployments during a frost period require an emergency change with VP approval. Plan around the freeze or get sign-off before pushing to the fleet.

```

```


```

