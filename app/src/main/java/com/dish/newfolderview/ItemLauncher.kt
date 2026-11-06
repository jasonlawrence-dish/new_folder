package com.dish.newfolderview

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast

/**
 * Launches a FolderItem. Apps via launcher intent (or explicit component);
 * web/zinc via ACTION_VIEW. Never throws — shows a toast on failure.
 *
 * NOTE: On a WS1 Launcher kiosk, the launcher allowlist still governs what
 * may start. If an app is not allowlisted, the OS/launcher may block it even
 * though this call is well-formed. That is a WS1 profile concern, not a bug here.
 */
object ItemLauncher {

    private const val TAG = "NFV.ItemLauncher"

    /** @return true if something was launched, false on failure (folder stays open). */
    fun launch(context: Context, item: FolderItem): Boolean {
        return when (item.type) {
            ItemType.APP -> launchApp(context, item)
            ItemType.WEB, ItemType.ZINC -> launchUrl(context, item)
        }
    }

    private fun launchApp(context: Context, item: FolderItem): Boolean {
        val pkg = item.packageName
        try {
            val intent: Intent? = item.explicitActivity?.let { act ->
                Intent().apply {
                    component = ComponentName(pkg, if (act.startsWith(".")) pkg + act else act)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } ?: context.packageManager.getLaunchIntentForPackage(pkg)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            if (intent == null) {
                notInstalled(context, item.label)
                return false
            }
            context.startActivity(intent)
            return true
        } catch (e: ActivityNotFoundException) {
            notInstalled(context, item.label)
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to launch ${item.label}: ${t.message}")
            toast(context, "Couldn't open ${item.label}")
        }
        return false
    }

    private fun launchUrl(context: Context, item: FolderItem): Boolean {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(item.target)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return true
        } catch (e: ActivityNotFoundException) {
            toast(context, "No app available to open ${item.label}")
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to open URL for ${item.label}: ${t.message}")
            toast(context, "Couldn't open ${item.label}")
        }
        return false
    }

    private fun notInstalled(context: Context, label: String) {
        Log.i(TAG, "App not installed / not launchable: $label")
        toast(context, "$label is not installed")
    }

    private fun toast(context: Context, msg: String) =
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
}
