package com.dish.newfolderview

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Environment
import android.util.Log
import java.io.File

/**
 * Resolves an icon Drawable for a FolderItem.
 *  - app + "default"  -> the app's real launcher icon
 *  - "@name"          -> bundled drawable res/drawable/ic_web_<name> (in-APK)
 *  - icon path ("/.pics/x.png" or absolute) -> load from external storage
 *  - anything missing  -> bundled fallback (R.mipmap/drawable) via caller
 *
 * Returns null if nothing could be loaded; caller supplies a fallback.
 */
object IconLoader {

    private const val TAG = "NFV.IconLoader"

    fun load(context: Context, item: FolderItem): Drawable? {
        // App default icon.
        if (item.type == ItemType.APP && item.usesDefaultIcon) {
            return try {
                context.packageManager.getApplicationIcon(item.packageName)
            } catch (t: Throwable) {
                Log.i(TAG, "No app icon for ${item.packageName}: ${t.message}")
                null
            }
        }

        // Bundled drawable reference: "@name" -> res/drawable/ic_web_<name>.
        // Lets web/zinc icons ship inside the APK (no external storage needed).
        val ic = item.icon.trim()
        if (ic.startsWith("@")) {
            val resName = "ic_web_" + ic.removePrefix("@").lowercase()
            return try {
                val id = context.resources.getIdentifier(resName, "drawable", context.packageName)
                if (id != 0) context.getDrawable(id) else {
                    Log.i(TAG, "Bundled icon not found: $resName")
                    null
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to load bundled icon $resName: ${t.message}")
                null
            }
        }

        // Explicit icon file path.
        val iconPath = item.icon
        if (iconPath.isNotEmpty() && !iconPath.equals("default", ignoreCase = true)) {
            val file = resolveIconFile(iconPath)
            if (file != null && file.exists() && file.canRead()) {
                return try {
                    val bmp = BitmapFactory.decodeFile(file.absolutePath)
                    if (bmp != null) BitmapDrawable(context.resources, bmp) else null
                } catch (t: Throwable) {
                    Log.w(TAG, "Failed to decode icon ${file.absolutePath}: ${t.message}")
                    null
                }
            } else {
                Log.i(TAG, "Icon file not found: $iconPath")
            }
        }
        return null
    }

    /** "/.pics/x.png" is relative to external storage root; absolute paths used as-is. */
    private fun resolveIconFile(path: String): File? {
        return try {
            if (path.startsWith("/sdcard/") || path.startsWith("/storage/")) {
                File(path)
            } else {
                File(Environment.getExternalStorageDirectory(), path.removePrefix("/"))
            }
        } catch (t: Throwable) {
            null
        }
    }
}
