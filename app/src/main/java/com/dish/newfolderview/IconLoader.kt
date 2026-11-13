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
 *  - image + "default" -> the image's OWN target rendered as the thumbnail
 *                         (so an image tile previews a mini version of itself)
 *  - "@name"          -> bundled drawable res/drawable/ic_web_<name> OR
 *                        ic_img_<name> (in-APK, no external storage needed)
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

        // Image tile asking for the default thumbnail: render the image itself
        // (its target) as the tile preview. This is what makes the QR show as a
        // small preview in the folder before it's tapped open full-screen.
        if (item.type == ItemType.IMAGE && item.usesDefaultIcon) {
            return loadRef(context, item.target)
        }

        // Otherwise load whatever the icon field points at.
        return loadRef(context, item.icon)
    }

    /**
     * Load a drawable from an image reference, which may be either a bundled
     * resource ("@name" -> ic_web_<name> or ic_img_<name>) or a file path
     * ("/.pics/x.png" or an absolute path on external storage).
     * Returns null if it can't be resolved; the caller falls back.
     */
    fun loadRef(context: Context, refRaw: String?): Drawable? {
        val ref = refRaw?.trim().orEmpty()
        if (ref.isEmpty() || ref.equals("default", ignoreCase = true)) return null

        // Bundled drawable reference: "@name" -> res/drawable/ic_web_<name>
        // or ic_img_<name>. Lets web/zinc icons AND bundled images ship inside
        // the APK (no external storage needed).
        if (ref.startsWith("@")) {
            val base = ref.removePrefix("@").lowercase()
            for (prefix in arrayOf("ic_img_", "ic_web_")) {
                try {
                    val id = context.resources.getIdentifier(
                        prefix + base, "drawable", context.packageName
                    )
                    if (id != 0) return context.getDrawable(id)
                } catch (t: Throwable) {
                    Log.w(TAG, "Failed to load bundled drawable $prefix$base: ${t.message}")
                }
            }
            Log.i(TAG, "Bundled drawable not found for ref: $ref")
            return null
        }

        // Explicit file path.
        val file = resolveIconFile(ref)
        if (file != null && file.exists() && file.canRead()) {
            return try {
                val bmp = BitmapFactory.decodeFile(file.absolutePath)
                if (bmp != null) BitmapDrawable(context.resources, bmp) else null
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to decode image ${file.absolutePath}: ${t.message}")
                null
            }
        }
        Log.i(TAG, "Image file not found: $ref")
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
