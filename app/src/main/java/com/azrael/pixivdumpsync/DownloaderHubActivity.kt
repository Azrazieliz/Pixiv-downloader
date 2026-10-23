package com.azrael.pixivdumpsync

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.azrael.galleryflow.GalleryDb
import java.net.URI

/** Launcher for both independently managed download engines. */
class DownloaderHubActivity : Activity() {
    private lateinit var savedSites: LinearLayout
    private val bg = Color.rgb(12, 15, 23)
    private val surface = Color.rgb(27, 32, 44)
    private val muted = Color.rgb(153, 170, 197)
    private val accent = Color.rgb(118, 181, 255)
    private val purple = Color.rgb(190, 144, 255)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        val scroll = ScrollView(this).apply {
            setBackgroundColor(bg)
            isFillViewport = true
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(19), dp(26), dp(19), dp(34))
        }
        scroll.addView(root)
        root.addView(label(getString(R.string.hub_brand_name), 12f, accent, true))
        root.addView(label(getString(R.string.hub_display_title), 31f, Color.WHITE, true).apply {
            setPadding(0, dp(8), 0, dp(4))
        })
        root.addView(label("One application. Independent site queues, catalogs, and download controls.", 13f, muted))

        root.addView(label("DOWNLOAD ENGINES", 12f, muted, true).apply {
            setPadding(0, dp(26), 0, dp(10))
        })
        val pixiv = tile(if (resources.getBoolean(R.bool.nocturne_preview)) "NOCTURNE / PIXIV" else "KUROHA", "Pixiv archive",
            "Following feed  ·  artists  ·  bookmarks  ·  tag blocking", accent)
        pixiv.setOnClickListener { startActivity(Intent(this, MainActivity::class.java)) }
        root.addView(pixiv, params(12))
        val gallery = tile(if (resources.getBoolean(R.bool.nocturne_preview)) "NOCTURNE / GALLERIES" else "KYORA", "Image sites & galleries",
            "Saved websites · booru sites · adult galleries · tag blocking", purple)
        gallery.setOnClickListener { openGalleries() }
        root.addView(gallery, params(12))

        root.addView(label("YOUR SITES", 12f, muted, true).apply {
            setPadding(0, dp(24), 0, dp(8))
        })
        root.addView(Button(this).apply {
            text = "+ Add image website or gallery"
            textSize = 15f
            setTextColor(bg)
            setBackgroundColor(accent)
            setOnClickListener { openGalleries(add = true) }
        }, params(12))
        savedSites = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(savedSites)
        root.addView(label(
            "Public HTML sites are supported generically. Sources requiring login, JavaScript or special paging need a dedicated adapter.",
            12f, muted).apply { setPadding(0, dp(18), 0, 0) })
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        renderSites()
    }

    private fun renderSites() {
        savedSites.removeAllViews()
        val entries = runCatching { GalleryDb(this).use { it.listEntities() } }.getOrDefault(emptyList())
        if (entries.isEmpty()) {
            savedSites.addView(label("No custom sites added yet.", 13f, muted).apply {
                setPadding(dp(8), dp(12), 0, 0)
            })
            return
        }
        entries.forEach { entry ->
            val domain = runCatching { URI(entry.canonicalUrl).host }.getOrNull().orEmpty()
            val card = tile(domain.uppercase().take(30),
                entry.displayName, if (entry.liveEnabled) "Live enabled" else "Manual only",
                if (entry.liveEnabled) accent else purple)
            card.setOnClickListener { openGalleries(siteId = entry.entityId) }
            savedSites.addView(card, params(8))
        }
    }

    private fun openGalleries(add: Boolean = false, siteId: String? = null) {
        val intent = Intent(this, com.azrael.galleryflow.MainActivity::class.java)
            .putExtra(com.azrael.galleryflow.MainActivity.EXTRA_ADD_SITE, add)
        if (siteId != null) intent.putExtra(com.azrael.galleryflow.MainActivity.EXTRA_SITE_ID, siteId)
        startActivity(intent)
    }

    private fun tile(kicker: String, title: String, subtitle: String, color: Int): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(17), dp(18), dp(18))
            background = GradientDrawable().apply {
                setColor(surface); cornerRadius = dp(16).toFloat()
                setStroke(dp(1), Color.argb(100, Color.red(color), Color.green(color), Color.blue(color)))
            }
            isClickable = true
            isFocusable = true
            addView(label(kicker, 11f, color, true))
            addView(label(title, 20f, Color.WHITE, true).apply { setPadding(0, dp(4), 0, 0) })
            addView(label(subtitle, 12.5f, muted).apply { setPadding(0, dp(6), 0, 0) })
        }

    private fun params(gap: Int) = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(gap) }
    private fun label(value: String, size: Float, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            text = value; textSize = size; setTextColor(color)
            if (bold) setTypeface(null, Typeface.BOLD)
        }
    private fun dp(px: Int) = (px * resources.displayMetrics.density).toInt()
}
