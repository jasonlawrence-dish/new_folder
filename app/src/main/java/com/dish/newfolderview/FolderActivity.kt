package com.dish.newfolderview

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Bundle
import android.view.WindowManager
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * The ONE activity. Shows a single folder's items as a FLOATING folder
 * popover over the (dimmed) launcher — mimics a native folder opening,
 * NOT a full-screen app. Tapping outside the card dismisses it.
 *
 * Which folder to show (priority order):
 *   1. Intent extra "folder"
 *   2. Deep link newfolderview://folder/<Name>
 *   3. Managed config "defaultFolder"
 *   4. First folder in the config
 *
 * Fixes vs. original Folderview:
 *   - No hardcoded folder names / no built-in grid (no self-nesting).
 *   - No forced orientation; grid reflows for portrait AND landscape.
 *   - Floating popover over the live background, not an opaque full app.
 *   - Never crashes on bad/empty config — shows a small notice.
 */
class FolderActivity : AppCompatActivity() {

    private val TAG = "NFV.FolderActivity"
    private lateinit var recycler: RecyclerView
    private lateinit var titleView: TextView
    private lateinit var emptyView: TextView

    // Columns inside the floating card (not screen-width derived).
    private val GRID_COLUMNS = 4

    // Tapped-tile anchor captured ONCE at launch as fractions of the launch
    // screen. WS1 only sends sourceBounds at launch (never on rotation), but
    // its grid preserves each tile's fractional position across orientations,
    // so we re-derive pixel position from these on every rotation.
    private var anchorValid = false
    private var anchorFx = 0f   // tile center X / screen width
    private var anchorFy = 0f   // tile center Y / screen height
    private var anchorFw = 0f   // tile width  / screen width
    private var anchorFh = 0f   // tile height / screen height

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_folder)
        // Dim the launcher behind the floating folder (native-folder look).
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.setDimAmount(0.6f)

        // --- DIAGNOSTIC PROBE: does WS1 Launcher pass the tapped tile's rectangle?
        // If sourceBounds is non-null we can open the
        // folder over the icon natively. Logged under tag NFV.SourceBounds.
        logSourceBounds()

        titleView = findViewById(R.id.folder_title)
        emptyView = findViewById(R.id.empty_notice)
        recycler = findViewById(R.id.grid)
        recycler.layoutManager = GridLayoutManager(this, GRID_COLUMNS)
        recycler.isNestedScrollingEnabled = false

        captureAnchor()

        // Tap on the scrim (outside the card) closes the folder.
        findViewById<View>(R.id.scrim).setOnClickListener { finish() }
        // Taps on the card itself must NOT fall through to the scrim.
        findViewById<View>(R.id.folder_card).setOnClickListener { /* consume */ }

        render()
    }

    private fun logSourceBounds() {
        val tag = "NFV.SourceBounds"
        try {
            val sb: Rect? = intent?.sourceBounds
            val dm = resources.displayMetrics
            Log.w(tag, "screen=${dm.widthPixels}x${dm.heightPixels} density=${dm.density}")
            Log.w(tag, "intent.sourceBounds = $sb")
            val extras = intent?.extras
            if (extras != null) {
                for (k in extras.keySet()) {
                    Log.w(tag, "extra[$k] = ${extras.get(k)}")
                }
            } else {
                Log.w(tag, "no intent extras")
            }
            Log.w(tag, "intent.data = ${intent?.data}")
        } catch (t: Throwable) {
            Log.w(tag, "probe failed: ${t.message}")
        }
    }

    /**
     * Capture the tapped tile as fractions of the LAUNCH screen, so we can
     * reposition correctly after rotation (WS1 never re-sends sourceBounds).
     */
    private fun captureAnchor() {
        val tile: Rect = intent?.sourceBounds ?: return
        val dm = resources.displayMetrics
        val w = dm.widthPixels.toFloat()
        val h = dm.heightPixels.toFloat()
        if (w <= 0f || h <= 0f) return
        anchorFx = ((tile.left + tile.right) / 2f) / w
        anchorFy = ((tile.top + tile.bottom) / 2f) / h
        anchorFw = tile.width() / w
        anchorFh = tile.height() / h
        anchorValid = true
    }

    /**
     * Position the floating card near the tile, using the stored fractional
     * anchor re-derived against the CURRENT screen size. Centers horizontally
     * on the (estimated) tile, pops above it (or below if no room), and clamps
     * fully on-screen. Safe to call on first layout and on every rotation.
     * No-op (keeps default top-start) if we never had tile bounds.
     */
    private fun positionCard() {
        if (!anchorValid) return
        val card = findViewById<View>(R.id.folder_card)
        card.post {
            try {
                val dm = resources.displayMetrics
                val screenW = dm.widthPixels
                val screenH = dm.heightPixels
                val cardW = card.width
                val cardH = card.height
                val gap = (12 * dm.density).toInt()
                val pad = (8 * dm.density).toInt()

                // Reconstruct an estimated tile rect in the CURRENT orientation.
                val cx = anchorFx * screenW
                val cy = anchorFy * screenH
                val tileH = (anchorFh * screenH).toInt()
                val tileTop = (cy - tileH / 2f).toInt()
                val tileBottom = (cy + tileH / 2f).toInt()

                // Horizontal: center on the tile, clamp to screen.
                var left = (cx - cardW / 2f).toInt()
                left = left.coerceIn(pad, (screenW - cardW - pad).coerceAtLeast(pad))

                // Vertical: prefer above the tile; if it would clip, place below.
                var top = tileTop - cardH - gap
                if (top < pad) {
                    top = tileBottom + gap
                }
                top = top.coerceIn(pad, (screenH - cardH - pad).coerceAtLeast(pad))

                val lp = card.layoutParams as FrameLayout.LayoutParams
                lp.gravity = android.view.Gravity.TOP or android.view.Gravity.START
                lp.leftMargin = left
                lp.topMargin = top
                card.layoutParams = lp
            } catch (t: Throwable) {
                Log.w(TAG, "positionCard failed: ${t.message}")
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Re-derive the card position for the new orientation from the stored
        // fractional anchor (fixes off-screen / mis-pinned folder on rotation).
        positionCard()
    }

    private fun render() {
        val loaded = ConfigSource.load(this)
        val result = ConfigParser.parse(loaded.text)

        if (result.warnings.isNotEmpty()) {
            Log.w(TAG, "Config warnings: ${result.warnings.joinToString(" | ")}")
        }

        val requested = requestedFolderName()
        val folder = pickFolder(result.folders, requested)

        if (folder == null || folder.items.isEmpty()) {
            applyTitle(folder?.name ?: (requested ?: getString(R.string.app_name)))
            recycler.visibility = View.GONE
            emptyView.visibility = View.VISIBLE
            emptyView.text = buildEmptyMessage(result, loaded.source)
            positionCard()
            return
        }

        applyTitle(folder.name)
        emptyView.visibility = View.GONE
        recycler.visibility = View.VISIBLE
        // Cap columns so a tiny folder doesn't stretch the card.
        (recycler.layoutManager as GridLayoutManager).spanCount =
            minOf(GRID_COLUMNS, folder.items.size).coerceAtLeast(1)
        recycler.adapter = FolderAdapter(folder.items) { item ->
            // Close the folder only if the launch actually succeeded.
            if (ItemLauncher.launch(this, item)) finish()
        }
        positionCard()
    }

    private fun pickFolder(folders: List<Folder>, requested: String?): Folder? {
        if (folders.isEmpty()) return null
        if (requested != null) {
            folders.firstOrNull { it.name.equals(requested, ignoreCase = true) }?.let { return it }
            Log.i(TAG, "Requested folder '$requested' not found; using first.")
        }
        managedDefaultFolder()?.let { def ->
            folders.firstOrNull { it.name.equals(def, ignoreCase = true) }?.let { return it }
        }
        return folders.first()
    }

    private fun requestedFolderName(): String? {
        intent?.getStringExtra("folder")?.takeIf { it.isNotBlank() }?.let { return it }
        intent?.data?.let { uri ->
            if (uri.scheme == "newfolderview") {
                return uri.lastPathSegment ?: uri.host
            }
        }
        return null
    }

    private fun managedDefaultFolder(): String? = try {
        val rm = getSystemService(RESTRICTIONS_SERVICE) as? android.content.RestrictionsManager
        rm?.applicationRestrictions?.getString("defaultFolder")?.takeIf { it.isNotBlank() }
    } catch (t: Throwable) { null }

    /**
     * Show the folder title, unless the name is meant to be "blank":
     * an empty/whitespace name, or the reserved tokens blank1..blank8
     * (any "blank" + optional digits, case-insensitive). Blank folders
     * render as a title-less grid popover.
     */
    private fun applyTitle(name: String) {
        if (isBlankName(name)) {
            titleView.visibility = View.GONE
        } else {
            titleView.visibility = View.VISIBLE
            titleView.text = name
        }
    }

    private fun isBlankName(name: String): Boolean {
        val n = name.trim()
        if (n.isEmpty()) return true
        return Regex("^blank\\d*$", RegexOption.IGNORE_CASE).matches(n)
    }

    private fun buildEmptyMessage(result: ConfigParser.Result, source: String): String {
        return if (result.folders.isEmpty()) {
            "No folder configuration found.\nSource checked: $source"
        } else {
            "This folder is empty."
        }
    }
}
