package com.dish.newfolderview

import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Full-screen, scan-optimized image viewer. Built for the pair/activation
 * workflow: an employee taps an image tile (e.g. a QR) and holds the tablet
 * so the CUSTOMER'S phone scans it up close.
 *
 * Scan tuning (the image doesn't need to be huge — the phone is inches away):
 *  - WHITE background => clean quiet-zone around the code + a consistent,
 *    high-contrast scan surface (unlike the dimmed launcher behind a folder).
 *  - MAX screen brightness => kills glossy-screen glare and sharpens the
 *    black/white edges scanners key on.
 *  - KEEP_SCREEN_ON => screen won't dim mid-scan while the phone lines up.
 *  - Image shown centered at a sensible size (fitCenter), NOT edge-to-edge.
 *
 * Launches nothing external, so it never touches the WS1 Launcher allowlist.
 * Tap anywhere, or Back, to dismiss. Never crashes on a bad/missing image —
 * shows a short notice instead.
 */
class ImageViewerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_IMAGE_REF = "image_ref"
        const val EXTRA_LABEL = "label"
        private const val TAG = "NFV.ImageViewer"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_viewer)

        // Keep the screen awake the whole time the code is shown.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val root = findViewById<View>(R.id.viewer_root)
        val image = findViewById<ImageView>(R.id.viewer_image)
        val notice = findViewById<TextView>(R.id.viewer_notice)
        val label = findViewById<TextView>(R.id.viewer_label)

        val ref = intent?.getStringExtra(EXTRA_IMAGE_REF)
        val caption = intent?.getStringExtra(EXTRA_LABEL).orEmpty()

        if (caption.isNotBlank()) {
            label.visibility = View.VISIBLE
            label.text = caption
        } else {
            label.visibility = View.GONE
        }

        val drawable = try {
            IconLoader.loadRef(this, ref)
        } catch (t: Throwable) {
            Log.w(TAG, "loadRef failed for '$ref': ${t.message}")
            null
        }

        if (drawable != null) {
            image.setImageDrawable(drawable)
            image.visibility = View.VISIBLE
            notice.visibility = View.GONE
        } else {
            image.visibility = View.GONE
            notice.visibility = View.VISIBLE
            notice.text = getString(R.string.viewer_image_missing)
            Log.w(TAG, "Could not load image for ref: $ref")
        }

        // Tap anywhere to dismiss.
        root.setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        // Push screen brightness to max for easiest scanning; restored
        // automatically when this window loses focus / is dismissed.
        window.attributes = window.attributes.apply {
            screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
        }
    }
}
