# New Folder View (`com.dish.newfolderview`)

A modern Kotlin rebuild of Rick's Folderview, designed for **WS1 Launcher** devices
(AE fully-managed, e.g. Samsung A11/SM-X200). WS1 Launcher owns and locks the home
screen; this app just displays a **configurable folder of app/web/zinc tiles** read
from a text file (or WS1 managed config) and launches them.

## Why this exists (bugs in Folderview 4.0.6 it fixes)
- **Hardcoded folder names** → custom names crashed (`String.equals` NPE at `main.java:584`).
  Here folder names are **data**; any `Folder:<Name>` works.
- **Empty sections crashed** the per-folder activity. Here empty folders are fine.
- **Forced portrait** regardless of device orientation. Here the grid **reflows**
  for portrait AND landscape; no forced orientation.
- **Built-in 9-folder grid / self-nesting.** Gone — only one activity, shows only
  what the config defines.
See `FOLDERVIEW_DECONSTRUCTION.md` for the full teardown.

## Config file format

Same grammar as the original. Default file name is `folders.txt` (override with the `configFileName` managed-config key; the old `wirelessfolder.txt` name still works if you set it there).
**The `NumberOfApps:` line is no longer needed** (ignored if present — it was the
old crash source). Just list items; order in the file = order on screen.

```
Folder:Wireless
app,Boost Unlock,com.dish.wireless.deviceunlock,default
app,Camera,com.sec.android.app.camera,default
app,YOOBIC ONE,com.ipelia.yoobicv3,default
web,Device Diagnostics,https://.../launch.html?co=...&code=...,/.pics/diag.png
```

Each item line = 4 comma-separated fields, **no spaces around commas**:

| Field | app | web / zinc |
|-------|-----|-----------|
| 1 type | `app` | `web` or `zinc` |
| 2 label | shown under icon | shown under icon |
| 3 target | process/package name (from WS1; may be `pkg/Activity`) | full URL |
| 4 icon | `default` (app's own icon) or `/.pics/x.png` | **must** be `/.pics/x.png` (not `default`) |

Rules:
- Process names are **exact, case-sensitive**.
- Not-installed apps are **skipped silently** (one file can serve many device types).
- Blank lines and lines starting with `#` or `'` are ignored.
- Multiple `Folder:` sections are allowed; see "Which folder shows" below.

## Where config comes from (priority order)
1. **WS1 Managed App Configuration** — admins set the `config` key (full text) under the
   app's Application Configuration. No file, no storage permission. Recommended on AE.
   Optional keys: `defaultFolder`, `configFileName`. Schema: `res/xml/app_restrictions.xml`.
2. **`/sdcard/folders.txt`** — config file (needs All-Files-Access).
3. **`filesDir/folders.txt`** — app-private fallback.

## Which folder shows (parameterized single app)
1. Intent extra `folder` (WS1 tile can pass it, if supported)
2. Deep link `newfolderview://folder/<Name>`
3. Managed `defaultFolder` key
4. First folder in the config

## Build
Requires the Gradle wrapper jar (not committed). From Android Studio: open the folder
and let it sync, or run `gradle wrapper` once to generate `gradlew`.

```
./gradlew testDebugUnitTest     # run parser crash-prevention tests
./gradlew assembleRelease       # build signed APK (needs keystore.properties)
./gradlew bumpVersion           # increment VERSION_CODE (do this every build)
```

### Signing / keystore
- Copy `keystore.properties.template` → `keystore.properties` and fill in.
- Generate a release keystore ONCE; store the `.jks` + passwords in the
  **enterprise password manager**. Without them you can never update this package.
- `keystore.properties` and `*.jks` are git-ignored.

### Versioning
`version.properties` holds `VERSION_CODE`/`VERSION_NAME`. **Bump every build.**

## Deploy on the A11 (WS1)
1. Upload the signed APK to WS1, assign to the boostdiag OG.
2. Add `com.dish.newfolderview` to the **WS1 Launcher allowlist** (AllowedApplicationId).
   NOTE: the launcher allowlist still governs what tiles can launch — folder contents and
   the allowlist must agree.
3. Deliver config via Managed App Configuration (preferred) or push `folders.txt`.
4. For web/zinc icons, ensure the `.png` exists in `/sdcard/.pics/`.
5. **Test on one tablet**, confirm folder renders in both orientations and every tile
   launches, before rolling to the OG. Disable the old Folderview product.

## Open items
- Confirm WS1 Launcher can pass an intent extra / launch a deep link (decides whether
  one app can serve multiple folders, or it's one-folder-per-install).
- Generate the Gradle wrapper jar + `gradlew`/`gradlew.bat` (not created here).
- Supply a real launcher icon if the vector placeholder isn't wanted.
