package com.dish.newfolderview

import android.content.Context
import android.content.RestrictionsManager
import android.os.Environment
import android.util.Log
import java.io.File

/**
 * Resolves the folder config from, in priority order:
 *   1. WS1 Managed App Configuration (AE-correct, no storage permission).
 *      WS1 pushes an app restriction key "config" containing the full text.
 *   2. External storage file: /sdcard/<fileName> (default folders.txt),
 *      for backward compatibility with existing WS1 file-actions.
 *   3. App's own files dir: filesDir/<fileName> (fallback delivery target).
 *
 * Returns the raw text plus which source produced it (for logging/UI).
 */
object ConfigSource {

    private const val TAG = "NFV.ConfigSource"
    const val DEFAULT_FILE = "folders.txt"

    /** Managed-config key WS1 admins set under App Configuration. */
    const val MANAGED_KEY_CONFIG = "config"
    const val MANAGED_KEY_FILENAME = "configFileName"

    data class Loaded(val text: String?, val source: String)

    fun load(context: Context): Loaded {
        // 1) Managed App Configuration
        try {
            val rm = context.getSystemService(Context.RESTRICTIONS_SERVICE) as? RestrictionsManager
            val restrictions = rm?.applicationRestrictions
            val managed = restrictions?.getString(MANAGED_KEY_CONFIG)
            if (!managed.isNullOrBlank()) {
                Log.i(TAG, "Loaded config from WS1 managed app configuration.")
                return Loaded(managed, "Managed App Config")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Managed config read failed: ${t.message}")
        }

        val fileName = managedFileName(context) ?: DEFAULT_FILE

        // 2) External storage root
        try {
            val ext = File(Environment.getExternalStorageDirectory(), fileName)
            if (ext.exists() && ext.canRead()) {
                Log.i(TAG, "Loaded config from external storage: ${ext.absolutePath}")
                return Loaded(ext.readText(Charsets.UTF_8), "External: ${ext.absolutePath}")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "External storage read failed: ${t.message}")
        }

        // 3) App files dir
        try {
            val internal = File(context.filesDir, fileName)
            if (internal.exists() && internal.canRead()) {
                Log.i(TAG, "Loaded config from internal files dir: ${internal.absolutePath}")
                return Loaded(internal.readText(Charsets.UTF_8), "Internal: ${internal.absolutePath}")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Internal read failed: ${t.message}")
        }

        Log.w(TAG, "No config found in any source.")
        return Loaded(null, "none")
    }

    private fun managedFileName(context: Context): String? {
        return try {
            val rm = context.getSystemService(Context.RESTRICTIONS_SERVICE) as? RestrictionsManager
            rm?.applicationRestrictions?.getString(MANAGED_KEY_FILENAME)?.takeIf { it.isNotBlank() }
        } catch (t: Throwable) {
            null
        }
    }
}
